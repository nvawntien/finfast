package com.finfast;

import com.finfast.common.money.VndAmount;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcFinancialTransactionRepository;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Entry;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(45)
class LedgerPersistenceIntegrationTests extends LedgerPersistenceTestSupport {

    @ParameterizedTest
    @EnumSource(Type.class)
    void completeFactsRoundTrip(Type type) {
        UUID id = UUID.randomUUID();
        String reference = "😀".repeat(64);
        VndAmount amount = new VndAmount(Long.MAX_VALUE);
        FinancialTransaction original = inTransaction(() -> {
            UUID treasury = type == Type.GENESIS ? fixtures.account("account_type", "TREASURY") : source;
            List<Entry> entries = type == Type.GENESIS
                    ? List.of(new Entry(UUID.randomUUID(), treasury, amount.value()))
                    : List.of(new Entry(UUID.randomUUID(), destination, amount.value()),
                            new Entry(UUID.randomUUID(), source, -amount.value()));
            FinancialTransaction value = switch (type) {
                case INTERNAL_TRANSFER -> FinancialTransaction.internalTransfer(id, reference, amount, 1, TIME, entries);
                case DEMO_ALLOCATION -> FinancialTransaction.demoAllocation(id, reference, amount, 1, TIME, entries);
                case GENESIS -> FinancialTransaction.genesis(id, reference, amount, 1, TIME, treasury, entries);
            };
            repository.insert(value);
            assertThat(repository.findById(id)).isPresent(); // Same transaction reads its own complete facts.
            assertThat(snapshotFromIndependentConnection().transactions()).isZero(); // No independent commit.
            return value;
        });
        FinancialTransaction stored = inTransaction(() -> repository.findById(id).orElseThrow());
        assertThat(stored.id()).isEqualTo(original.id());
        assertThat(stored.reference()).isEqualTo(reference);
        assertThat(stored.type()).isEqualTo(type);
        assertThat(stored.amount()).isEqualTo(amount);
        assertThat(stored.commitSeq()).isEqualTo(1);
        assertThat(stored.recordedAt()).isEqualTo(TIME.truncatedTo(ChronoUnit.MICROS));
        assertThat(original.recordedAt()).isEqualTo(TIME);
        assertThat(stored.entries()).containsExactlyElementsOf(original.entries().stream()
                .sorted(Comparator.comparingLong(Entry::deltaVnd).thenComparing(Entry::id)).toList());
        assertThat(ledgerJdbc.queryForObject("SELECT currency FROM financial_transaction WHERE id = ?", String.class, id))
                .isEqualTo("VND");
        assertThat(inTransaction(() -> repository.findById(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void mandatoryBoundaryUsesRealSpringProxies() {
        assertThat(AopUtils.isAopProxy(repository)).isTrue();
        assertThat(AopUtils.isAopProxy(positions)).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        Snapshot before = snapshot();
        assertThatThrownBy(() -> repository.insert(fact(Type.INTERNAL_TRANSFER, 1)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(positions::reserveNext).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> repository.findById(UUID.randomUUID())).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void resourceGuardRejectsUnproxiedAndWrongDataSourceWrites() {
        Snapshot before = snapshot();
        assertThatThrownBy(() -> new JdbcFinancialTransactionRepository(ledgerJdbc).insert(fact(Type.INTERNAL_TRANSFER, 1)))
                .isInstanceOf(IllegalTransactionStateException.class);
        JdbcFinancialTransactionRepository wrongResource = new JdbcFinancialTransactionRepository(new JdbcTemplate(isolatedDataSource()));
        assertThatThrownBy(() -> inTransaction(() -> {
            wrongResource.insert(fact(Type.INTERNAL_TRANSFER, 1));
            return null;
        })).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void rejectsReadOnlyWrites() {
        var readOnly = transaction();
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.execute(status -> positions.reserveNext())).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> readOnly.execute(status -> {
            repository.insert(fact(Type.INTERNAL_TRANSFER, 1));
            return null;
        })).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(snapshot()).isEqualTo(new Snapshot(0, 0, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {Connection.TRANSACTION_REPEATABLE_READ, Connection.TRANSACTION_SERIALIZABLE})
    void rejectsWrongWriteIsolation(int isolation) {
        var wrongIsolation = transaction();
        wrongIsolation.setIsolationLevel(isolation);
        assertThatThrownBy(() -> wrongIsolation.execute(status -> positions.reserveNext()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> wrongIsolation.execute(status -> {
            repository.insert(fact(Type.INTERNAL_TRANSFER, 1));
            return null;
        })).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(snapshot()).isEqualTo(new Snapshot(0, 0, 0));
    }

    @Test
    void sequentialPositionsCommitWithCompleteFacts() {
        seedDataset(1);
        for (long expected : List.of(2L, 3L)) {
            long reserved = inTransaction(() -> {
                long sequence = positions.reserveNext();
                repository.insert(fact(Type.INTERNAL_TRANSFER, sequence));
                return sequence;
            });
            assertThat(reserved).isEqualTo(expected);
        }
        assertThat(snapshot()).isEqualTo(new Snapshot(3, 5, 3));
    }

    @Test
    void outerRollbackAfterSuccessfulReturnRemovesAllWrites() {
        seedDataset(1);
        Snapshot before = snapshot();
        transaction().executeWithoutResult(status -> {
            long sequence = positions.reserveNext();
            FinancialTransaction fact = fact(Type.INTERNAL_TRANSFER, sequence);
            repository.insert(fact);
            assertThat(repository.findById(fact.id())).isPresent();
            assertThat(snapshotFromIndependentConnection()).isEqualTo(before);
            status.setRollbackOnly();
        });
        assertThat(snapshot()).isEqualTo(before);
        long reused = inTransaction(() -> {
            long sequence = positions.reserveNext();
            repository.insert(fact(Type.INTERNAL_TRANSFER, sequence));
            return sequence;
        });
        assertThat(reused).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "reference", "position"})
    void duplicateHeaderRollsBackPositionAndAllRows(String duplicate) {
        seedDataset(1);
        FinancialTransaction original = inTransaction(() -> {
            FinancialTransaction value = fact(Type.INTERNAL_TRANSFER, positions.reserveNext());
            repository.insert(value);
            return value;
        });
        Snapshot before = snapshot();
        DataAccessException error = assertThrows(DataAccessException.class, () -> inTransaction(() -> {
            long sequence = positions.reserveNext();
            repository.insert(pair(Type.INTERNAL_TRANSFER,
                    duplicate.equals("id") ? original.id() : UUID.randomUUID(),
                    duplicate.equals("reference") ? original.reference() : "OTHER",
                    duplicate.equals("position") ? original.commitSeq() : sequence,
                    UUID.randomUUID(), UUID.randomUUID(), destination, TIME));
            return null;
        }));
        assertSqlState(error, "23505");
        assertThat(error.getMostSpecificCause().getMessage()).contains(switch (duplicate) {
            case "id" -> "pk_financial_transaction";
            case "reference" -> "uq_financial_transaction_reference";
            default -> "uq_financial_transaction_commit_seq";
        });
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void secondEntryFailureRollsBackHeaderFirstEntryAndCounter(boolean invalidAccount) {
        Seed seed = seedDataset(1);
        Snapshot before = snapshot();
        DataAccessException error = assertThrows(DataAccessException.class, () -> inTransaction(() -> {
            long sequence = positions.reserveNext();
            repository.insert(pair(Type.INTERNAL_TRANSFER, UUID.randomUUID(), "PARTIAL", sequence,
                    UUID.randomUUID(), invalidAccount ? UUID.randomUUID() : seed.genesisEntryId(),
                    invalidAccount ? DatabaseFixtures.UNKNOWN_ID : destination, TIME));
            return null;
        }));
        assertSqlState(error, invalidAccount ? "23503" : "23505");
        assertThat(error.getMostSpecificCause().getMessage()).contains(invalidAccount
                ? "fk_ledger_entry_account" : "pk_ledger_entry");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void caughtRepositoryFailureStillPreventsOuterCommit() {
        Seed seed = seedDataset(1);
        Snapshot before = snapshot();
        assertThatThrownBy(() -> inTransaction(() -> {
            long sequence = positions.reserveNext();
            assertThrows(DataAccessException.class, () -> repository.insert(pair(Type.INTERNAL_TRANSFER,
                    UUID.randomUUID(), "CAUGHT", sequence, UUID.randomUUID(), seed.genesisEntryId(), destination, TIME)));
            return null;
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void missingDatasetDoesNotCreateSupply() {
        assertThatThrownBy(() -> inTransaction(positions::reserveNext))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("missing or counter exhausted");
        assertThat(snapshot()).isEqualTo(new Snapshot(0, 0, 0));
        assertThat(ledgerJdbc.queryForObject("SELECT count(*) FROM account WHERE account_type = 'TREASURY'", Long.class)).isZero();
    }

    @Test
    void maximumPositionThenExhaustionNeverWraps() {
        seedDataset(Long.MAX_VALUE - 1);
        long last = inTransaction(() -> {
            long sequence = positions.reserveNext();
            repository.insert(fact(Type.INTERNAL_TRANSFER, sequence));
            return sequence;
        });
        assertThat(last).isEqualTo(Long.MAX_VALUE);
        Snapshot before = snapshot();
        assertThatThrownBy(() -> inTransaction(positions::reserveNext)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(snapshot().position()).isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "one", "extra", "unbalanced", "wrongAmount", "negativeGenesis"})
    void corruptFactsFailReconstruction(String corruption) {
        UUID id = inTransaction(() -> {
            UUID transaction = fixtures.transaction("type", corruption.equals("negativeGenesis") ? "GENESIS" : "INTERNAL_TRANSFER",
                    "amount_vnd", corruption.equals("wrongAmount") ? 101L : 100L);
            if (!corruption.equals("empty")) {
                fixtures.entry(transaction, source, -100);
            }
            if (!List.of("empty", "one", "negativeGenesis").contains(corruption)) {
                fixtures.entry(transaction, destination, corruption.equals("unbalanced") ? 99 : 100);
            }
            if (corruption.equals("extra")) {
                fixtures.entry(transaction, fixtures.account(), 1);
            }
            return transaction;
        });
        assertThatThrownBy(() -> inTransaction(() -> repository.findById(id)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("Invalid or incomplete");
    }

    @Test
    void nonfiniteTimestampIsNotReinterpretedAsAnInstant() {
        UUID id = inTransaction(() -> {
            UUID transaction = fixtures.transaction("committed_at", java.time.OffsetDateTime.MAX);
            fixtures.entry(transaction, source, -100);
            fixtures.entry(transaction, destination, 100);
            return transaction;
        });
        assertThat(ledgerJdbc.queryForObject("SELECT isfinite(committed_at) FROM financial_transaction WHERE id = ?",
                Boolean.class, id)).isFalse();
        assertThatThrownBy(() -> inTransaction(() -> repository.findById(id)))
                .isInstanceOf(DataIntegrityViolationException.class).hasRootCauseMessage("Nonfinite persisted timestamp");
    }

    @Test
    void malformedUnicodeCannotBeSilentlyReplacedByJdbc() {
        seedDataset(1);
        Snapshot before = snapshot();
        assertThatThrownBy(() -> inTransaction(() -> {
            long sequence = positions.reserveNext();
            repository.insert(pair(Type.INTERNAL_TRANSFER, UUID.randomUUID(), "\uD800", sequence,
                    UUID.randomUUID(), UUID.randomUUID(), destination, TIME));
            return null;
        })).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("valid Unicode");
        assertThat(snapshot()).isEqualTo(before);
    }

    private Snapshot snapshotFromIndependentConnection() {
        try (Connection connection = database.open()) {
            JdbcTemplate observer = IsolatedTestDatabase.jdbc(connection);
            return observer.queryForObject("""
                    SELECT (SELECT count(*) FROM financial_transaction) AS transactions,
                           (SELECT count(*) FROM ledger_entry) AS entries,
                           COALESCE((SELECT last_commit_seq FROM dataset_control WHERE singleton_id = 1), 0) AS position
                    """, (row, number) -> new Snapshot(row.getLong("transactions"), row.getLong("entries"), row.getLong("position")));
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
    }

    static void assertSqlState(DataAccessException error, String state) {
        assertThat(error.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
                sql -> assertThat(sql.getSQLState()).isEqualTo(state));
    }
}
