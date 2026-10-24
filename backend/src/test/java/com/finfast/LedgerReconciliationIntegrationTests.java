package com.finfast;

import com.finfast.ledger.api.PostingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;

import java.math.BigInteger;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.finfast.LedgerReconciliationVerifier.Code;
import static com.finfast.LedgerReconciliationVerifier.Violation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerReconciliationIntegrationTests extends LedgerOrdinaryPostingTestSupport {

    @Test
    void freshGenesisIncludesEveryEmptyCustomerAndCountsSupplyOnce() {
        initialize(100);
        UUID extra = fixtures.account();
        String before = financialState();
        var result = reconciliation();
        assertThat(result.isConsistent()).as("%s", result.violations()).isTrue();
        assertThat(result.accounts()).extracting(LedgerReconciliationVerifier.AccountBalance::id)
                .containsExactlyInAnyOrder(treasury, source, destination, extra);
        assertThat(result.accounts()).filteredOn(a -> !a.id().equals(treasury))
                .allSatisfy(a -> {
                    assertThat(a.materialized()).isZero();
                    assertThat(a.ledger()).isZero();
                });
        assertThat(result.totalBalance()).isEqualTo(BigInteger.valueOf(100));
        assertThat(result.initialSupply()).isEqualTo(BigInteger.valueOf(100));
        assertThat(result.transactionIds()).hasSize(1);
        assertThat(result.entryCount()).isEqualTo(BigInteger.ONE);
        assertThat(result.lastPosition()).isEqualTo(1);
        assertThat(financialState()).isEqualTo(before);
        assertThrows(UnsupportedOperationException.class, () -> result.accounts().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.transactionIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.violations().clear());
    }

    @Test
    void absentDatasetAndGenesisCannotBeHealthy() {
        var result = reconciliation();
        assertThat(result.isConsistent()).isFalse();
        assertThat(result.violations()).contains(new Violation(Code.DATASET_COUNT, null, "1", "0"),
                new Violation(Code.GENESIS_COUNT, null, "1", "0"), new Violation(Code.TREASURY_COUNT, null, "1", "0"));
        assertThat(result.initialSupply()).isNull();
        assertThat(result.lastPosition()).isNull();
        assertThat(snapshot().transactions()).isZero();
    }

    @ParameterizedTest
    @ValueSource(longs = {1, Long.MAX_VALUE})
    void allocationsAndOppositeRepeatedTransfersReconcileAtMonetaryBoundaries(long amount) {
        initialize(amount);
        String genesisBefore = ledgerJdbc.queryForObject(
                "SELECT to_jsonb(t)::text FROM financial_transaction t WHERE type = 'GENESIS'", String.class);
        fund(source, amount);
        assertFinancialInvariants();
        for (int i = 0; i < 3; i++) {
            assertPending(inTransaction(() -> transfers.postTransfer(transfer(source, destination, amount))));
            assertFinancialInvariants();
            assertPending(inTransaction(() -> transfers.postTransfer(transfer(destination, source, amount))));
            assertFinancialInvariants();
        }
        assertThat(reconciliation().transactionIds()).hasSize(8);
        assertThat(reconciliation().entryCount()).isEqualTo(BigInteger.valueOf(15));
        assertThat(balance(source)).isEqualTo(amount);
        assertThat(balance(treasury)).isZero();
        assertThat(ledgerJdbc.queryForObject(
                "SELECT to_jsonb(t)::text FROM financial_transaction t WHERE type = 'GENESIS'", String.class))
                .isEqualTo(genesisBefore);
    }

    @Test
    void deterministicMixedWorkloadAccountsForEveryCommittedFactAndNoRejectedOrRolledBackEffects() {
        initialize(1000);
        List<UUID> committed = new ArrayList<>(reconciliation().transactionIds());
        checkFacts(committed);
        committed.add(assertPending(inTransaction(() -> allocations.postAllocation(allocation(source, 200)))).transactionId());
        checkFacts(committed);
        committed.add(assertPending(inTransaction(() -> transfers.postTransfer(transfer(source, destination, 30)))).transactionId());
        checkFacts(committed);
        committed.add(assertPending(inTransaction(() -> transfers.postTransfer(transfer(destination, source, 10)))).transactionId());
        checkFacts(committed);
        String before = financialState();
        assertThat(inTransaction(() -> transfers.postTransfer(transfer(destination, source, 100))))
                .isInstanceOf(PostingResult.Rejected.class);
        assertThat(financialState()).isEqualTo(before);
        checkFacts(committed);
        committed.add(assertPending(inTransaction(() -> allocations.postAllocation(allocation(destination, 50)))).transactionId());
        checkFacts(committed);
        before = financialState();
        assertThrows(IllegalStateException.class, () -> inTransaction(() -> {
            assertPending(transfers.postTransfer(transfer(source, destination, 20)));
            throw new IllegalStateException("test outer technical failure after Pending");
        }));
        assertThat(financialState()).isEqualTo(before);
        checkFacts(committed);
        committed.add(assertPending(inTransaction(() -> transfers.postTransfer(transfer(destination, source, 5)))).transactionId());
        checkFacts(committed);
        assertThat(balance(source)).isEqualTo(185);
        assertThat(balance(destination)).isEqualTo(65);
        assertThat(balance(treasury)).isEqualTo(750);
    }

    @ParameterizedTest
    @ValueSource(strings = {"balance", "entry", "outer"})
    void technicalFailureLeavesExactlyThePreviousCommittedSnapshot(String stage) {
        initialize(100);
        fund(source, 50);
        String before = financialState();
        var expected = reconciliation();
        if (stage.equals("balance")) {
            pairProbe.afterBalanceWrite = written -> {
                if (written == 1) {
                    ledgerJdbc.execute("SELECT 1 / 0");
                }
            };
        } else if (stage.equals("entry")) {
            failAfterInsert("ledger_entry");
        }
        RuntimeException failure = assertThrows(RuntimeException.class, () -> inTransaction(() -> {
            assertPending(transfers.postTransfer(transfer(source, destination, 10)));
            throw new IllegalStateException("test outer rollback");
        }));
        if (!stage.equals("outer")) {
            assertThat(failure).isInstanceOfSatisfying(DataAccessException.class,
                    error -> LedgerPersistenceIntegrationTests.assertSqlState(error, stage.equals("balance") ? "22012" : "P0001"));
        }
        assertThat(financialState()).as("after transaction completion").isEqualTo(before);
        assertThat(reconciliation()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"none,0,0", "one,-100,0", "magnitude,-99,99", "nonzero,-100,99", "positive,100,100", "extra,-100,100"})
    void detectsCommittedMalformedOrdinaryFacts(String shape, long debit, long credit) {
        initialize(1000);
        UUID third = fixtures.account();
        UUID id = inTransaction(() -> {
            UUID tx = fixtures.transaction("commit_seq", 2L);
            if (!shape.equals("none")) {
                fixtures.entry(tx, source, debit);
            }
            if (!shape.equals("none") && !shape.equals("one")) {
                fixtures.entry(tx, destination, credit);
            }
            if (shape.equals("extra")) {
                fixtures.entry(tx, third, 1);
            }
            ledgerJdbc.update("UPDATE dataset_control SET last_commit_seq = last_commit_seq + 1");
            return tx;
        });
        String before = financialState();
        var result = reconciliation();
        assertThat(result.isConsistent()).isFalse();
        assertThat(result.violations()).anySatisfy(v -> {
            assertThat(v.subjectId()).isEqualTo(id);
            assertThat(v.code().name()).startsWith("ORDINARY_");
        });
        if (shape.equals("magnitude")) {
            assertThat(result.violations()).contains(new Violation(Code.ORDINARY_DEBITS, id, "1", "0"),
                    new Violation(Code.ORDINARY_CREDITS, id, "1", "0"));
        } else if (shape.equals("nonzero")) {
            assertThat(result.violations()).contains(new Violation(Code.ORDINARY_NET, id, "0", "-1"));
        } else if (shape.equals("none") || shape.equals("one") || shape.equals("extra")) {
            assertThat(result.violations()).contains(new Violation(Code.ORDINARY_ENTRIES, id, "2",
                    shape.equals("none") ? "0" : shape.equals("one") ? "1" : "3"));
        }
        assertThat(financialState()).isEqualTo(before);
        assertThat(reconciliation()).as("deterministic findings and no repair").isEqualTo(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingEntry", "wrongDelta", "wrongAccount", "extraEntry", "supply", "position", "noDataset"})
    void detectsIncompleteGenesisAndDatasetStatePermittedByStructuralConstraints(String defect) {
        UUID tx = inTransaction(() -> {
            treasury = fixtures.account("account_type", "TREASURY", "balance_vnd", 100L);
            long position = defect.equals("position") ? 2L : 1L;
            UUID genesisId = fixtures.transaction("type", "GENESIS", "commit_seq", position);
            if (!defect.equals("missingEntry")) {
                fixtures.entry(genesisId, defect.equals("wrongAccount") ? source : treasury,
                        defect.equals("wrongDelta") ? 99 : 100);
            }
            if (defect.equals("extraEntry")) {
                fixtures.entry(genesisId, source, 1);
            }
            if (!defect.equals("noDataset")) {
                fixtures.dataset(new DatabaseFixtures.DatasetReferences(treasury, genesisId),
                        "last_commit_seq", position, "initial_supply_vnd", defect.equals("supply") ? 101L : 100L);
            }
            return genesisId;
        });
        Code code = switch (defect) {
            case "missingEntry", "extraEntry" -> Code.GENESIS_ENTRIES;
            case "wrongDelta" -> Code.GENESIS_DELTA;
            case "wrongAccount" -> Code.GENESIS_ACCOUNT;
            case "supply" -> Code.GENESIS_SUPPLY;
            case "position" -> Code.GENESIS_POSITION;
            default -> Code.DATASET_COUNT;
        };
        assertThat(reconciliation().violations()).anySatisfy(v -> {
            assertThat(v.code()).isEqualTo(code);
            assertThat(v.subjectId()).isEqualTo(defect.equals("noDataset") ? null : tx);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void detectsCounterAheadOfFactsOrGappedPostingPositions(boolean gap) {
        initialize(100);
        if (gap) {
            inTransaction(() -> {
                UUID tx = fixtures.transaction("commit_seq", 3L);
                fixtures.entry(tx, source, -100);
                fixtures.entry(tx, destination, 100);
                return null;
            });
        } else {
            ledgerJdbc.update("UPDATE dataset_control SET last_commit_seq = last_commit_seq + 1");
        }
        assertThat(reconciliation().violations()).contains(new Violation(Code.COUNTER, null, gap ? "3" : "1", gap ? "1" : "2"));
        if (gap) {
            assertThat(reconciliation().violations()).contains(new Violation(Code.POSITION_RANGE, null, "3", "2"));
        }
    }

    @Test
    void reportsExactBalanceDriftAndSupplyWithoutRepair() {
        initialize(100);
        fund(source, 40);
        ledgerJdbc.update("UPDATE account SET balance_vnd = 41 WHERE id = ?", source);
        String corrupt = financialState();
        assertThat(reconciliation().violations()).containsExactly(
                new Violation(Code.ACCOUNT_BALANCE, source, "40", "41"), new Violation(Code.SUPPLY, null, "100", "101"));
        assertThat(financialState()).isEqualTo(corrupt);
    }

    @Test
    void totalMaterializedSupplyCanExceedBigintWithoutTruncation() {
        initialize(Long.MAX_VALUE);
        ledgerJdbc.update("UPDATE account SET balance_vnd = ? WHERE id IN (?, ?)", Long.MAX_VALUE, source, destination);
        BigInteger actual = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3));
        assertThat(reconciliation().totalBalance()).isEqualTo(actual);
        assertThat(reconciliation().violations()).contains(new Violation(Code.SUPPLY, null, "9223372036854775807", actual.toString()));
    }

    @Test
    void offsettingBalanceDriftFailsEvenWhenTotalSupplyStillMatches() {
        initialize(100);
        fund(source, 40);
        inTransaction(() -> {
            ledgerJdbc.update("UPDATE account SET balance_vnd = 41 WHERE id = ?", source);
            ledgerJdbc.update("UPDATE account SET balance_vnd = 59 WHERE id = ?", treasury);
            return null;
        });
        var result = reconciliation();
        assertThat(result.totalBalance()).isEqualTo(result.initialSupply());
        assertThat(result.violations()).containsExactlyInAnyOrder(
                new Violation(Code.ACCOUNT_BALANCE, source, "40", "41"),
                new Violation(Code.ACCOUNT_BALANCE, treasury, "60", "59"));
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MAX_VALUE, Long.MIN_VALUE})
    void arbitrarySignedAggregatesBeyondBigintRemainExact(long delta) {
        initialize(100);
        inTransaction(() -> {
            for (long sequence = 2; sequence <= 3; sequence++) {
                UUID tx = fixtures.transaction("commit_seq", sequence);
                fixtures.entry(tx, source, delta);
                ledgerJdbc.update("UPDATE dataset_control SET last_commit_seq = last_commit_seq + 1");
            }
            return null;
        });
        BigInteger signed = BigInteger.valueOf(delta).multiply(BigInteger.TWO);
        assertThat(reconciliation().violations()).contains(new Violation(Code.ACCOUNT_BALANCE, source, signed.toString(), "0"));
    }

    @Test
    void nonfiniteTimestampIsAnInvalidPersistedFact() {
        initialize(100);
        UUID tx = fixtures.transaction("commit_seq", 2L, "committed_at", OffsetDateTime.MAX);
        assertThat(reconciliation().violations()).anySatisfy(v -> {
            assertThat(v.code()).isEqualTo(Code.TRANSACTION_METADATA);
            assertThat(v.subjectId()).isEqualTo(tx);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "currency", "position", "secondGenesis", "participant", "genesisLink", "treasuryLink", "negativeBalance"})
    void impossibleCorruptionIsRejectedByExistingDatabaseGuards(String corruption) {
        initialize(100);
        String before = financialState();
        DataAccessException error = assertThrows(DataAccessException.class, () -> inTransaction(() -> {
            switch (corruption) {
                case "amount" -> fixtures.transaction("commit_seq", 2L, "amount_vnd", 0L);
                case "currency" -> fixtures.transaction("commit_seq", 2L, "currency", "USD");
                case "position" -> fixtures.transaction("commit_seq", 1L);
                case "secondGenesis" -> fixtures.transaction("commit_seq", 2L, "type", "GENESIS");
                case "participant" -> {
                    UUID tx = fixtures.transaction("commit_seq", 2L);
                    fixtures.entry(tx, source, -100);
                    fixtures.entry(tx, source, 100);
                }
                case "genesisLink" -> fixtures.dataset(new DatabaseFixtures.DatasetReferences(treasury,
                        fixtures.transaction("commit_seq", 2L)), "last_commit_seq", 2L);
                case "treasuryLink" -> fixtures.dataset(new DatabaseFixtures.DatasetReferences(source,
                        reconciliation().transactionIds().getFirst()));
                default -> ledgerJdbc.update("UPDATE account SET balance_vnd = -1 WHERE id = ?", source);
            }
            return null;
        }));
        LedgerPersistenceIntegrationTests.assertSqlState(error,
                List.of("position", "secondGenesis", "participant").contains(corruption) ? "23505" : "23514");
        assertThat(financialState()).isEqualTo(before);
        assertFinancialInvariants();
    }

    @Test
    void technicalReadFailurePropagatesAndInvalidSnapshotConfigurationIsRejected() throws Exception {
        LedgerReconciliationVerifier verifier = new LedgerReconciliationVerifier(database);
        try (Connection connection = database.open()) {
            assertThrows(IllegalArgumentException.class, () -> verifier.inspectSnapshot(connection));
            connection.setAutoCommit(false);
            assertThrows(IllegalArgumentException.class, () -> verifier.inspectSnapshot(connection));
            connection.rollback();
        }
        try (Connection connection = database.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            var observer = IsolatedTestDatabase.jdbc(connection);
            assertThat(observer.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
            assertThat(observer.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("repeatable read");
            var error = assertThrows(DataAccessException.class,
                    () -> observer.update("UPDATE account SET balance_vnd = 0 WHERE id = ?", source));
            LedgerPersistenceIntegrationTests.assertSqlState(error, "25006");
            connection.rollback();
        }
        // Preserve both migrations; a separate isolated permission context makes the SELECT fail.
        try (Connection connection = database.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            IsolatedTestDatabase.jdbc(connection).execute("SET LOCAL ROLE pg_read_all_stats");
            var error = assertThrows(DataAccessException.class, () -> verifier.inspectSnapshot(connection));
            LedgerPersistenceIntegrationTests.assertSqlState(error, "42501");
            connection.rollback();
        }
    }

    private void checkFacts(List<UUID> expected) {
        var result = reconciliation();
        assertThat(result.isConsistent()).as("%s", result.violations()).isTrue();
        assertThat(result.transactionIds()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(result.entryCount()).isEqualTo(BigInteger.valueOf(2L * expected.size() - 1));
        assertThat(result.lastPosition()).isEqualTo(expected.size());
    }

    private static PostingResult.Pending assertPending(PostingResult result) {
        assertThat(result).isInstanceOf(PostingResult.Pending.class);
        return (PostingResult.Pending) result;
    }
}
