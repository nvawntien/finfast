package com.finfast;

import com.finfast.ledger.api.GenesisDatasetInspection;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcGenesisDatasetInspection;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Ledger-only inspection evidence; real Accounts, protected operator setup and process restart are separate. */
@Timeout(45)
class GenesisDatasetInspectionIntegrationTests extends LedgerPersistenceTestSupport {
    private GenesisDatasetInspection inspection;

    @BeforeEach
    void registerInspectionProxy() {
        ledgerContext.registerBean(JdbcGenesisDatasetInspection.class, () -> new JdbcGenesisDatasetInspection(ledgerJdbc));
        inspection = ledgerContext.getBean(JdbcGenesisDatasetInspection.class);
        assertThat(AopUtils.isAopProxy(inspection)).isTrue();
    }

    @Test
    void freshLedgerIsAbsentAndReadOnlySnapshotIsActuallyEnforced() {
        assertThat(inspection.findCommitted()).isEmpty();
        readTransaction().executeWithoutResult(status -> {
            assertThat(ledgerJdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
            assertThat(ledgerJdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("repeatable read");
            assertThat(inspection.findCommitted()).isEmpty();
            DataAccessException failure = assertThrows(DataAccessException.class, () -> fixtures.transaction());
            LedgerPersistenceIntegrationTests.assertSqlState(failure, "25006");
            status.setRollbackOnly();
        });
        assertThat(inspection.findCommitted()).isEmpty();
    }

    @Test
    void committedGenesisRetainsOriginalFactsAcrossRepeatedReadsAndContextRecreation() {
        UUID[] ids = inTransaction(() -> {
            UUID treasury = fixtures.account("account_type", "TREASURY");
            UUID genesis = fixtures.transaction("type", "GENESIS", "commit_seq", 1L,
                    "reference", "  ORIGINAL-GENESIS  ", "amount_vnd", Long.MAX_VALUE);
            fixtures.entry(genesis, treasury, Long.MAX_VALUE);
            fixtures.dataset(new DatabaseFixtures.DatasetReferences(treasury, genesis), "initial_supply_vnd", Long.MAX_VALUE);
            return new UUID[]{treasury, genesis};
        });
        var expected = new GenesisDatasetInspection.InitializedDataset(ids[0], ids[1], "  ORIGINAL-GENESIS  ",
                new com.finfast.common.money.VndAmount(Long.MAX_VALUE), DatabaseFixtures.RECORDED_AT.toInstant());
        assertThat(inspection.findCommitted()).contains(expected);
        assertThat(inspection.findCommitted()).contains(expected);
        try (var recreated = readContext()) {
            assertThat(recreated.getBean(GenesisDatasetInspection.class).findCommitted()).contains(expected);
        }
        assertThat(snapshot()).isEqualTo(new Snapshot(1, 1, 1));
    }

    @Test
    void subsequentOrdinaryPostingDoesNotChangeGenesisIdentityOrInitialSupply() {
        seedDataset(1);
        var before = inspection.findCommitted();
        inTransaction(() -> {
            repository.insert(fact(Type.INTERNAL_TRANSFER, positions.reserveNext()));
            return null;
        });
        assertThat(inspection.findCommitted()).isEqualTo(before);
        assertThat(snapshot()).isEqualTo(new Snapshot(2, 3, 2));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void independentInspectionExcludesPendingSetupAndReflectsCommitOrRollback(boolean commit) {
        try (var reader = Executors.newVirtualThreadPerTaskExecutor()) {
            transaction().executeWithoutResult(status -> {
                seedDataset(1);
                assertThat(snapshot()).isEqualTo(new Snapshot(1, 1, 1));
                try {
                    assertThat(reader.submit(inspection::findCommitted).get(10, TimeUnit.SECONDS)).isEmpty();
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
                if (!commit) {
                    status.setRollbackOnly();
                }
            });
        }
        assertThat(inspection.findCommitted().isPresent()).isEqualTo(commit);
    }

    @Test
    void sharedReadSnapshotRemainsStableAcrossConcurrentCommitThenFreshReadSeesWinner() {
        try (var writer = Executors.newVirtualThreadPerTaskExecutor()) {
            readTransaction().executeWithoutResult(status -> {
                assertThat(inspection.findCommitted()).isEmpty();
                try {
                    writer.submit(() -> seedDataset(1)).get(10, TimeUnit.SECONDS);
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
                assertThat(inspection.findCommitted()).isEmpty();
            });
        }
        assertThat(inspection.findCommitted()).isPresent();
    }

    @Test
    void failedSetupMustUnwindBeforeFreshCommittedInspection() {
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            seedDataset(1);
            assertThrows(DataAccessException.class, () -> ledgerJdbc.queryForObject("SELECT 1 / 0", Integer.class));
            assertThatThrownBy(inspection::findCommitted).isInstanceOf(IllegalTransactionStateException.class);
            throw new IllegalStateException("Synthetic setup failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(inspection.findCommitted()).isEmpty();
        seedDataset(1);
        assertThat(inspection.findCommitted()).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENESIS", "INTERNAL_TRANSFER", "DEMO_ALLOCATION"})
    void orphanFinancialFactsAreInconsistentRatherThanUninitialized(String type) {
        inTransaction(() -> fixtures.transaction("type", type, "commit_seq", 1L));
        assertThatThrownBy(inspection::findCommitted).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingEntry", "negativeEntry", "wrongAmount", "wrongAccount", "extraEntry",
            "wrongSupply", "wrongPosition", "counterBehind", "positionGap", "infiniteTime"})
    void inconsistentCommittedGenesisOrControlFailsWithoutRepair(String corruption) {
        inTransaction(() -> {
            UUID treasury = fixtures.account("account_type", "TREASURY");
            long sequence = corruption.equals("wrongPosition") ? 2L : 1L;
            UUID genesis = fixtures.transaction("type", "GENESIS", "commit_seq", sequence,
                    "committed_at", corruption.equals("infiniteTime") ? OffsetDateTime.MAX : DatabaseFixtures.RECORDED_AT);
            if (!corruption.equals("missingEntry")) {
                fixtures.entry(genesis, corruption.equals("wrongAccount") ? destination : treasury,
                        corruption.equals("negativeEntry") ? -100 : corruption.equals("wrongAmount") ? 99 : 100);
            }
            if (corruption.equals("extraEntry")) {
                fixtures.entry(genesis, destination, 1);
            }
            fixtures.dataset(new DatabaseFixtures.DatasetReferences(treasury, genesis),
                    "initial_supply_vnd", corruption.equals("wrongSupply") ? 101L : 100L,
                    "last_commit_seq", sequence);
            if (corruption.equals("counterBehind") || corruption.equals("positionGap")) {
                fixtures.transaction("commit_seq", corruption.equals("positionGap") ? 3L : 2L);
            }
            return null;
        });
        Snapshot before = snapshot();
        assertThatThrownBy(inspection::findCommitted).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void actualResourceGuardRejectsUnproxiedUnboundAndMismatchedConnections() {
        assertThatThrownBy(() -> new JdbcGenesisDatasetInspection(ledgerJdbc).findCommitted())
                .isInstanceOf(IllegalTransactionStateException.class);
        readTransaction().executeWithoutResult(status -> {
            var mismatched = new JdbcGenesisDatasetInspection(new JdbcTemplate(isolatedDataSource()));
            assertThatThrownBy(mismatched::findCommitted).isInstanceOf(IllegalTransactionStateException.class);
        });
        var dataSource = ledgerContext.getBean(DataSource.class);
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        org.springframework.transaction.support.TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        try {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
            assertThatThrownBy(() -> new JdbcGenesisDatasetInspection(ledgerJdbc).findCommitted())
                    .isInstanceOf(IllegalTransactionStateException.class);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
            org.springframework.transaction.support.TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        }
    }

    @Test
    void explicitGuardRejectsBoundAutocommitEvenWithReadOnlyThreadMetadata() throws Exception {
        var dataSource = ledgerContext.getBean(DataSource.class);
        try (var connection = database.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
            assertThat(connection.getAutoCommit()).isTrue();
            var holder = new org.springframework.jdbc.datasource.ConnectionHolder(connection);
            org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(dataSource, holder);
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            org.springframework.transaction.support.TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
            try {
                assertThatThrownBy(() -> new JdbcGenesisDatasetInspection(ledgerJdbc).findCommitted())
                        .isInstanceOf(IllegalTransactionStateException.class);
            } finally {
                org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(dataSource);
                org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
                org.springframework.transaction.support.TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            }
        }
        assertThat(inspection.findCommitted()).isEmpty();
    }

    @Test
    void rejectsReadWriteAndWrongIsolationCallerInsteadOfReadingPendingFacts() {
        assertThatThrownBy(() -> inTransaction(inspection::findCommitted)).isInstanceOf(IllegalTransactionStateException.class);
        var readCommitted = transaction();
        readCommitted.setReadOnly(true);
        assertThatThrownBy(() -> readCommitted.execute(status -> inspection.findCommitted()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private TransactionTemplate readTransaction() {
        var template = transaction();
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        template.setReadOnly(true);
        return template;
    }

    private AnnotationConfigApplicationContext readContext() {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, this::isolatedDataSource);
        context.register(Transactions.class, JdbcGenesisDatasetInspection.class);
        context.refresh();
        return context;
    }
}
