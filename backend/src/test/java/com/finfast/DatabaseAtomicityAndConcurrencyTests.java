package com.finfast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

@Timeout(45)
class DatabaseAtomicityAndConcurrencyTests extends PostgresTestSupport {

    private IsolatedTestDatabase database;

    @BeforeEach
    void createDatabaseForRealCommitTests(TestInfo info) throws SQLException {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        database = IsolatedTestDatabase.create(postgres, info.getTestMethod().orElseThrow().getName());
    }

    @Test
    void partialFinancialWriteFailureRollsBack() throws SQLException {
        Seed seed = seed();
        Snapshot before = snapshot(seed.accountId());
        try (Connection transaction = database.open()) {
            transaction.setAutoCommit(false);
            try {
                JdbcTemplate writer = IsolatedTestDatabase.jdbc(transaction);
                DatabaseFixtures fixtures = new DatabaseFixtures(writer, 1000);
                writeFinancialFixtures(writer, fixtures, seed.accountId());
                assertSqlFailure("23514", "ck_ledger_entry_delta_nonzero",
                        () -> fixtures.entry(fixtures.transaction(), seed.accountId(), 0));
            } finally {
                transaction.rollback();
            }
        }
        assertThat(snapshot(seed.accountId())).isEqualTo(before);
    }

    @Test
    void invalidAuditRollsBackFinancialWrites() throws SQLException {
        Seed seed = seed();
        Snapshot before = snapshot(seed.accountId());
        try (Connection transaction = database.open()) {
            transaction.setAutoCommit(false);
            try {
                JdbcTemplate writer = IsolatedTestDatabase.jdbc(transaction);
                DatabaseFixtures fixtures = new DatabaseFixtures(writer, 1000);
                UUID posting = writeFinancialFixtures(writer, fixtures, seed.accountId());
                assertSqlFailure("23514", "ck_admin_audit_reason_nonblank", () -> fixtures.audit(
                        "admin_user_id", seed.adminId(), "target_account_id", seed.accountId(),
                        "action", "DEMO_ALLOCATION", "transaction_id", posting,
                        "before_status", null, "after_status", null, "reason", ""));
            } finally {
                transaction.rollback();
            }
        }
        assertThat(snapshot(seed.accountId())).isEqualTo(before);
    }

    @Test
    void conflictingOutcomeRollsBackFinancialWrites() throws SQLException {
        Seed seed = seed();
        try (Connection setup = database.open()) {
            setup.setAutoCommit(false);
            new DatabaseFixtures(IsolatedTestDatabase.jdbc(setup), 100)
                    .outcome("initiator_user_id", seed.initiatorId(), "idempotency_key", "reserved-key");
            setup.commit();
        }
        Snapshot before = snapshot(seed.accountId());
        try (Connection transaction = database.open()) {
            transaction.setAutoCommit(false);
            try {
                JdbcTemplate writer = IsolatedTestDatabase.jdbc(transaction);
                DatabaseFixtures fixtures = new DatabaseFixtures(writer, 1000);
                UUID posting = writeFinancialFixtures(writer, fixtures, seed.accountId());
                assertSqlFailure("23505", "uq_request_outcome_scope", () -> fixtures.outcome(
                        "initiator_user_id", seed.initiatorId(), "idempotency_key", "reserved-key",
                        "final_kind", "COMPLETED", "transaction_id", posting));
            } finally {
                transaction.rollback();
            }
        }
        assertThat(snapshot(seed.accountId())).isEqualTo(before);
        try (Connection verification = database.open()) {
            assertThat(IsolatedTestDatabase.jdbc(verification).queryForObject(
                    "SELECT final_kind FROM request_outcome WHERE idempotency_key = 'reserved-key'", String.class))
                    .isEqualTo("REJECTED");
        }
    }

    @Test
    void counterIncrementRollsBackAfterLaterFailure() throws SQLException {
        seedDataset();
        try (Connection transaction = database.open()) {
            transaction.setAutoCommit(false);
            try {
                JdbcTemplate writer = IsolatedTestDatabase.jdbc(transaction);
                assertThat(increment(writer)).isEqualTo(11);
                assertSqlFailure("23514", "ck_financial_transaction_amount_positive",
                        () -> new DatabaseFixtures(writer, 1000).transaction("amount_vnd", 0L));
            } finally {
                transaction.rollback();
            }
        }
        try (Connection verification = database.open()) {
            assertThat(counter(IsolatedTestDatabase.jdbc(verification))).isEqualTo(10);
            assertThat(IsolatedTestDatabase.jdbc(verification).queryForObject(
                    "SELECT count(*) FROM financial_transaction", Long.class)).isEqualTo(1);
        }
    }

    @Test
    void concurrentCounterIncrementsSerialize() throws Exception {
        seedDataset();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection first = database.open(); Connection observer = database.open()) {
            first.setAutoCommit(false);
            JdbcTemplate firstJdbc = IsolatedTestDatabase.jdbc(first);
            JdbcTemplate observerJdbc = IsolatedTestDatabase.jdbc(observer);
            int firstPid = pid(firstJdbc);
            assertThat(increment(firstJdbc)).isEqualTo(11);
            CountDownLatch started = new CountDownLatch(1);
            AtomicInteger secondPid = new AtomicInteger();
            try {
                Future<Long> second = executor.submit(() -> {
                    try (Connection connection = database.open()) {
                        connection.setAutoCommit(false);
                        try {
                            JdbcTemplate worker = IsolatedTestDatabase.jdbc(connection);
                            secondPid.set(pid(worker));
                            started.countDown();
                            long sequence = increment(worker);
                            connection.commit();
                            return sequence;
                        } finally {
                            connection.rollback();
                        }
                    } finally {
                        started.countDown();
                    }
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                awaitBlocked(observerJdbc, secondPid.get(), firstPid, second);
                assertThat(secondPid.get()).isNotEqualTo(firstPid);
                assertThat(counter(observerJdbc)).as("uncommitted increment is invisible").isEqualTo(10);
                first.commit();
                assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(12);
                assertThat(counter(observerJdbc)).isEqualTo(12);
            } finally {
                first.rollback();
            }
        } finally {
            stop(executor);
        }
    }

    @Test
    void concurrentOutcomeScopeAllowsOneCommit() throws Exception {
        Seed seed = seed();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection first = database.open(); Connection observer = database.open()) {
            first.setAutoCommit(false);
            JdbcTemplate firstJdbc = IsolatedTestDatabase.jdbc(first);
            JdbcTemplate observerJdbc = IsolatedTestDatabase.jdbc(observer);
            UUID winner = new DatabaseFixtures(firstJdbc, 1000).outcome("initiator_user_id", seed.initiatorId(),
                    "idempotency_key", "race-key");
            int firstPid = pid(firstJdbc);
            CountDownLatch started = new CountDownLatch(1);
            AtomicInteger secondPid = new AtomicInteger();
            try {
                Future<String> second = executor.submit(() -> {
                    try (Connection connection = database.open()) {
                        connection.setAutoCommit(false);
                        try {
                            JdbcTemplate worker = IsolatedTestDatabase.jdbc(connection);
                            secondPid.set(pid(worker));
                            started.countDown();
                            try {
                                new DatabaseFixtures(worker, 2000).outcome("initiator_user_id", seed.initiatorId(),
                                        "idempotency_key", "race-key", "rejection_code", "OTHER_REJECTION");
                                connection.commit();
                                return "COMMITTED";
                            } catch (DataAccessException error) {
                                assertThat(error.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
                                    assertThat(cause.getSQLState()).isEqualTo("23505");
                                    assertThat(cause.getMessage()).contains("uq_request_outcome_scope");
                                });
                                connection.rollback();
                                return "23505";
                            }
                        } finally {
                            connection.rollback();
                        }
                    } finally {
                        started.countDown();
                    }
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                awaitBlocked(observerJdbc, secondPid.get(), firstPid, second);
                assertThat(secondPid.get()).isNotEqualTo(firstPid);
                assertThat(observerJdbc.queryForObject("SELECT count(*) FROM request_outcome", Long.class)).isZero();
                first.commit();
                assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("23505");
                assertThat(observerJdbc.queryForObject("SELECT count(*) FROM request_outcome", Long.class)).isEqualTo(1);
                assertThat(observerJdbc.queryForObject("SELECT id FROM request_outcome", UUID.class)).isEqualTo(winner);
                assertThat(observerJdbc.queryForObject("SELECT rejection_code FROM request_outcome", String.class))
                        .isEqualTo("INSUFFICIENT_FUNDS");
            } finally {
                first.rollback();
            }
        } finally {
            stop(executor);
        }
    }

    private Seed seed() throws SQLException {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            DatabaseFixtures fixtures = new DatabaseFixtures(IsolatedTestDatabase.jdbc(connection));
            Seed seed = new Seed(fixtures.account("balance_vnd", 100L), fixtures.user("role", "ADMIN"), fixtures.user());
            connection.commit();
            return seed;
        }
    }

    private void seedDataset() throws SQLException {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            new DatabaseFixtures(IsolatedTestDatabase.jdbc(connection)).dataset(10);
            connection.commit();
        }
    }

    private UUID writeFinancialFixtures(JdbcTemplate writer, DatabaseFixtures fixtures, UUID account) {
        assertThat(writer.update("UPDATE account SET balance_vnd = balance_vnd + 25 WHERE id = ?", account)).isEqualTo(1);
        UUID transaction = fixtures.transaction("amount_vnd", 25L);
        fixtures.entry(transaction, account, 25);
        assertThat(writer.queryForObject("SELECT balance_vnd FROM account WHERE id = ?", Long.class, account)).isEqualTo(125);
        return transaction;
    }

    private Snapshot snapshot(UUID account) throws SQLException {
        try (Connection connection = database.open()) {
            JdbcTemplate reader = IsolatedTestDatabase.jdbc(connection);
            Map<String, Long> counts = new LinkedHashMap<>();
            for (String table : new String[]{"app_user", "customer", "account", "financial_transaction", "ledger_entry",
                    "dataset_control", "request_outcome", "admin_audit_event"}) {
                counts.put(table, reader.queryForObject("SELECT count(*) FROM public." + table, Long.class));
            }
            return new Snapshot(reader.queryForObject("SELECT balance_vnd FROM account WHERE id = ?", Long.class, account), counts);
        }
    }

    private static long increment(JdbcTemplate jdbc) {
        return jdbc.queryForObject("UPDATE dataset_control SET last_commit_seq = last_commit_seq + 1 RETURNING last_commit_seq", Long.class);
    }

    private static long counter(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT last_commit_seq FROM dataset_control", Long.class);
    }

    private static int pid(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private static void awaitBlocked(JdbcTemplate observer, int blocked, int blocker, Future<?> worker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (worker.isDone()) {
                worker.get(1, TimeUnit.SECONDS); // Propagate worker failures instead of hiding them as coordination failures.
                fail("Worker completed before PostgreSQL lock contention was observed");
            }
            if (Boolean.TRUE.equals(observer.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, blocker, blocked))) {
                return;
            }
            Thread.onSpinWait(); // Bounded observation of actual database waits; no scheduling sleeps.
        }
        fail("PostgreSQL did not report backend %s blocked by %s within 3 seconds", blocked, blocker);
    }

    private static void assertSqlFailure(String state, String message, Runnable statement) {
        assertThatThrownBy(statement::run).isInstanceOfSatisfying(DataAccessException.class, error ->
                assertThat(error.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
                    assertThat(cause.getSQLState()).isEqualTo(state);
                    assertThat(cause.getMessage()).contains(message);
                }));
    }

    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).as("worker executor terminated").isTrue();
    }

    private record Seed(UUID accountId, UUID adminId, UUID initiatorId) {
    }

    private record Snapshot(long balance, Map<String, Long> tableCounts) {
    }
}
