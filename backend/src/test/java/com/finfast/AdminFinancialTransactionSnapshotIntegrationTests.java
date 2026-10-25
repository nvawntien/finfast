package com.finfast;

import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcCommittedFinancialTransactions;
import com.finfast.ledger.internal.application.usecase.AdminFinancialTransactionQueryService;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(45)
class AdminFinancialTransactionSnapshotIntegrationTests extends AdminFinancialQueriesTestSupport {
    @Test
    void interceptedReadOnlySnapshotIsEnforcedByPostgresAndJoinsTheCaller() {
        seedDataset(1);
        readTransaction().executeWithoutResult(status -> {
            String snapshot = ledgerJdbc.queryForObject("SELECT pg_current_snapshot()::text", String.class);
            assertThat(queries.listCommitted().items()).hasSize(1);
            assertThat(identities.snapshots.values()).containsOnly(snapshot);
            var error = assertThrows(DataAccessException.class, () -> fixtures.transaction());
            LedgerPersistenceIntegrationTests.assertSqlState(error, "25006");
            status.setRollbackOnly();
        });
        assertThat(queries.listCommitted().items()).hasSize(1);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pendingGenesisIsInvisibleAndOnlyCommitMakesItVisible(boolean commit) throws Exception {
        try (var reader = Executors.newVirtualThreadPerTaskExecutor()) {
            transaction().executeWithoutResult(status -> {
                seedDataset(1);
                try {
                    assertThat(reader.submit(queries::listCommitted).get(10, TimeUnit.SECONDS).items()).isEmpty();
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                if (!commit) {
                    status.setRollbackOnly();
                }
            });
        }
        assertThat(queries.listCommitted().items()).hasSize(commit ? 1 : 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pendingOrdinaryPostingAndItsNewIdentityRemainInvisibleUntilCommit(boolean commit) {
        seedDataset(1);
        var before = queries.listCommitted();
        try (var reader = Executors.newVirtualThreadPerTaskExecutor()) {
            transaction().executeWithoutResult(status -> {
                UUID recipient = fixtures.account("account_number", "new exact identity");
                long position = positions.reserveNext();
                UUID id = fixtures.transaction("commit_seq", position);
                fixtures.entry(id, source, -100);
                try {
                    // Even a temporarily incomplete posting must not poison an independent committed listing.
                    assertThat(reader.submit(queries::listCommitted).get(10, TimeUnit.SECONDS)).isEqualTo(before);
                    fixtures.entry(id, recipient, 100);
                    assertThat(reader.submit(queries::listCommitted).get(10, TimeUnit.SECONDS)).isEqualTo(before);
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                if (!commit) {
                    status.setRollbackOnly();
                }
            });
        }
        var after = queries.listCommitted();
        assertThat(after.items()).hasSize(commit ? 2 : 1);
        if (commit) {
            assertThat(after.items().getFirst().entries().getLast().accountNumber().value()).isEqualTo("new exact identity");
        } else {
            assertThat(after).isEqualTo(before);
        }
    }

    @Test
    void heldRepeatableReadSnapshotRemainsStableAcrossConcurrentCommit() throws Exception {
        seedDataset(1);
        try (var writer = Executors.newVirtualThreadPerTaskExecutor()) {
            readTransaction().executeWithoutResult(status -> {
                var before = queries.listCommitted();
                try {
                    writer.submit(() -> append(Type.INTERNAL_TRANSFER, 100, "concurrent", TIME, source, destination))
                            .get(10, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                assertThat(queries.listCommitted()).isEqualTo(before);
            });
        }
        assertThat(queries.listCommitted().items()).hasSize(2);
    }

    @Test
    void identityLookupsAndFactsShareOneSnapshotWhileNewAccountAndPostingCommit() throws Exception {
        seedDataset(1);
        append(Type.INTERNAL_TRANSFER, 100, "old transfer", TIME, source, destination);
        var expected = queries.listCommitted();
        identities.calls.clear();
        identities.snapshots.clear();
        var lookupReached = new CountDownLatch(1);
        var writerCommitted = new CountDownLatch(1);
        identities.lookup = id -> {
            lookupReached.countDown();
            await(writerCommitted);
            var identity = identities.read(id);
            assertThat(ledgerJdbc.queryForObject("SELECT pg_current_snapshot()::text", String.class))
                    .isEqualTo(identities.snapshots.get(id));
            assertThat(ledgerJdbc.queryForObject("SELECT count(*) FROM account WHERE account_number = 'new concurrent account'", Long.class))
                    .isZero();
            return identity;
        };
        try (var reader = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = reader.submit(queries::listCommitted);
            try {
                await(lookupReached); // The complete financial read has finished, before identity SELECTs.
                inTransaction(() -> {
                    UUID newAccount = fixtures.account("account_number", "new concurrent account");
                    append(Type.INTERNAL_TRANSFER, 1, "new concurrent transfer", TIME, source, newAccount);
                    return null;
                });
            } finally {
                writerCommitted.countDown();
            }
            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(expected);
        }
        assertThat(identities.snapshots.values().stream().distinct()).hasSize(1);
        identities.lookup = identities::read;
        var fresh = queries.listCommitted();
        assertThat(fresh.items()).hasSize(3);
        assertThat(fresh.items().getFirst().entries().getLast().accountNumber().value()).isEqualTo("new concurrent account");
    }

    @Test
    void writeTransactionMisuseRejectsInsteadOfSuspendingOrCommittingPendingFinancialEffects() {
        seedDataset(1);
        var before = queries.listCommitted();
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            append(Type.INTERNAL_TRANSFER, 100, "must roll back", TIME, source, destination);
            queries.listCommitted();
        })).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(queries.listCommitted()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(ints = {Connection.TRANSACTION_READ_COMMITTED, Connection.TRANSACTION_SERIALIZABLE})
    void incorrectCallerIsolationFails(int isolation) {
        var caller = readTransaction();
        caller.setIsolationLevel(isolation);
        assertThatThrownBy(() -> caller.execute(status -> queries.listCommitted()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(identities.calls).isEmpty();
    }

    @Test
    void noProxyOrCallerTransactionCannotReadFacts() {
        assertThatThrownBy(() -> new AdminFinancialTransactionQueryService(committedFacts, identities).listCommitted())
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> new JdbcCommittedFinancialTransactions(ledgerJdbc).listCommitted())
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(committedFacts::listCommitted).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void mismatchedDataSourceAndUnboundThreadMetadataCannotMasqueradeAsASnapshot() {
        readTransaction().executeWithoutResult(status -> {
            var wrong = new JdbcCommittedFinancialTransactions(new JdbcTemplate(isolatedDataSource()));
            assertThatThrownBy(wrong::listCommitted).isInstanceOf(IllegalTransactionStateException.class);
        });
        setSnapshotMetadata();
        try {
            assertThat(TransactionSynchronizationManager.hasResource(ledgerJdbc.getDataSource())).isFalse();
            assertThatThrownBy(() -> new JdbcCommittedFinancialTransactions(ledgerJdbc).listCommitted())
                    .isInstanceOf(IllegalTransactionStateException.class);
        } finally {
            clearSnapshotMetadata();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"autocommit", "readWrite", "readCommitted"})
    void actualJdbcStateMustMatchReadOnlyRepeatableReadDespiteCompatibleThreadMetadata(String defect) throws Exception {
        var dataSource = ledgerJdbc.getDataSource();
        try (var connection = database.open()) {
            connection.setReadOnly(!defect.equals("readWrite"));
            connection.setTransactionIsolation(defect.equals("readCommitted")
                    ? Connection.TRANSACTION_READ_COMMITTED : Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(defect.equals("autocommit"));
            TransactionSynchronizationManager.bindResource(dataSource, new ConnectionHolder(connection));
            setSnapshotMetadata();
            try {
                assertThatThrownBy(() -> new JdbcCommittedFinancialTransactions(ledgerJdbc).listCommitted())
                        .isInstanceOf(IllegalTransactionStateException.class);
            } finally {
                TransactionSynchronizationManager.unbindResource(dataSource);
                clearSnapshotMetadata();
                if (!connection.getAutoCommit()) {
                    connection.rollback();
                }
            }
        }
    }

    private static void setSnapshotMetadata() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ);
    }

    private static void clearSnapshotMetadata() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).as("bounded concurrency barrier").isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
