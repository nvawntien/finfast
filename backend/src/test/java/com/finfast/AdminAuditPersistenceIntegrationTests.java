package com.finfast;

import com.finfast.administration.internal.adapter.outbound.persistence.JdbcAdminAuditRepository;
import com.finfast.administration.internal.domain.entity.AdminAuditEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@Timeout(45)
class AdminAuditPersistenceIntegrationTests extends AdminAuditPersistenceTestSupport {

    @ParameterizedTest
    @MethodSource("reasons")
    void completeAuditCommitsWithExactReason(String reason) throws Exception {
        AdminAuditEvent event = new AdminAuditEvent(UUID.randomUUID(), admin, target, reason, allocation, TIME);
        caller.run(() -> repository.insert(event));

        try (Connection reader = database.open()) {
            JdbcTemplate observer = IsolatedTestDatabase.jdbc(reader);
            assertThat(count(observer)).isEqualTo(1);
            observer.queryForObject("SELECT * FROM public.admin_audit_event", (row, number) -> {
                assertThat(row.getObject("id", UUID.class)).isEqualTo(event.id());
                assertThat(row.getObject("admin_user_id", UUID.class)).isEqualTo(admin);
                assertThat(row.getObject("target_account_id", UUID.class)).isEqualTo(target);
                assertThat(row.getObject("transaction_id", UUID.class)).isEqualTo(allocation);
                assertThat(row.getString("action")).isEqualTo("DEMO_ALLOCATION");
                assertThat(row.getString("outcome")).isEqualTo("SUCCESS");
                assertThat(row.getString("before_status")).isNull();
                assertThat(row.getString("after_status")).isNull();
                assertThat(row.getString("reason")).isEqualTo(reason);
                assertThat(row.getObject("created_at", OffsetDateTime.class).toInstant())
                        .isEqualTo(TIME.truncatedTo(ChronoUnit.MICROS));
                return true;
            });
        }
        assertThat(event.createdAt()).isEqualTo(TIME);
        // Adapter ownership is limited to the audit: all prerequisites remain unchanged.
        assertThat(auditJdbc.queryForObject("SELECT count(*) FROM financial_transaction", Long.class)).isEqualTo(1);
        assertThat(auditJdbc.queryForObject("SELECT count(*) FROM ledger_entry", Long.class)).isZero();
        assertThat(auditJdbc.queryForObject("SELECT count(*) FROM request_outcome", Long.class)).isZero();
        assertThat(auditJdbc.queryForObject("SELECT balance_vnd FROM account WHERE id = ?", Long.class, target)).isZero();
    }

    static Stream<String> reasons() {
        return Stream.of("Approved synthetic demo funds", " \t exact reason \n ", "e\u0301", "Demo 😀",
                "a".repeat(500), "😀".repeat(500), "e\u0301".repeat(250), "a" + " ".repeat(499));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-23T01:02:03.123456Z", "2026-10-23T01:02:03.123456789Z",
            "2026-10-23T01:02:03.999999999Z", "1969-12-31T23:59:59.999999999Z",
            "-4712-01-01T00:00:00Z"})
    void timestampRoundTripTruncatesWithoutRoundingOrDependingOnSessionZone(String timestamp) throws Exception {
        Instant supplied = Instant.parse(timestamp);
        AdminAuditEvent event = new AdminAuditEvent(UUID.randomUUID(), admin, target, "Time check", allocation, supplied);
        caller.run(() -> {
            auditJdbc.execute("SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh'");
            repository.insert(event);
        });
        Instant stored = auditJdbc.queryForObject("SELECT created_at FROM admin_audit_event",
                (row, number) -> row.getObject(1, OffsetDateTime.class).toInstant());
        assertThat(stored).isEqualTo(supplied.truncatedTo(ChronoUnit.MICROS));
        assertThat(event.createdAt()).isEqualTo(supplied);
        assertThat(auditJdbc.queryForObject("SELECT isfinite(created_at) FROM admin_audit_event", Boolean.class)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-4713-12-31T23:59:59Z", "-1000000000-01-01T00:00:00Z",
            "+1000000000-12-31T23:59:59.999999999Z"})
    void unrepresentableJavaOrDriverTimestampsFailWithoutInfinity(String timestamp) {
        AdminAuditEvent event = new AdminAuditEvent(UUID.randomUUID(), admin, target, "Time check", allocation,
                Instant.parse(timestamp));
        assertThatThrownBy(() -> caller.run(() -> repository.insert(event))).isInstanceOf(DateTimeException.class);
        assertThat(committedCount()).isZero();
    }

    @Test
    void databaseTimestampRangeFailurePropagates() {
        AdminAuditEvent event = new AdminAuditEvent(UUID.randomUUID(), admin, target, "Time check", allocation,
                Instant.parse("+300000-01-01T00:00:00Z"));
        DataAccessException error = assertThrows(DataAccessException.class, () -> caller.run(() -> repository.insert(event)));
        assertSqlState(error, "22008", "out of range");
        assertThat(committedCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "transaction"})
    void duplicateIdentityOrTransactionFailsWithoutIgnoringOrReplacing(String duplicate) throws Exception {
        AdminAuditEvent original = event();
        caller.run(() -> repository.insert(original));
        UUID anotherAllocation = transaction().execute(status -> fixtures.transaction("type", "DEMO_ALLOCATION"));
        AdminAuditEvent duplicateEvent = new AdminAuditEvent(duplicate.equals("id") ? original.id() : UUID.randomUUID(),
                admin, target, "Different reason", duplicate.equals("transaction") ? allocation : anotherAllocation, TIME);
        DataAccessException error = assertThrows(DataAccessException.class, () -> caller.run(() -> repository.insert(duplicateEvent)));
        assertSqlState(error, "23505", duplicate.equals("id") ? "pk_admin_audit_event" : "uq_admin_audit_transaction");
        assertThat(committedCount()).isEqualTo(1);
        assertThat(auditJdbc.queryForObject("SELECT reason FROM admin_audit_event", String.class)).isEqualTo(original.reason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "target", "transaction"})
    void missingReferencedRowsRemainTechnicalFailures(String missing) {
        AdminAuditEvent event = new AdminAuditEvent(UUID.randomUUID(), missing.equals("admin") ? UUID.randomUUID() : admin,
                missing.equals("target") ? UUID.randomUUID() : target, "FK check",
                missing.equals("transaction") ? UUID.randomUUID() : allocation, TIME);
        DataAccessException error = assertThrows(DataAccessException.class, () -> caller.run(() -> repository.insert(event)));
        assertSqlState(error, "23503", "fk_admin_audit_" + missing);
        assertThat(committedCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"transaction", "before_status", "after_status", "outcome"})
    void databaseEnforcesSuccessfulAllocationShapeForImpossibleDomainStates(String invalid) {
        DataAccessException error = assertThrows(DataAccessException.class, () -> caller.run(() -> fixtures.audit(
                "admin_user_id", admin, "target_account_id", target, "action", "DEMO_ALLOCATION",
                "reason", "Shape check", "transaction_id", invalid.equals("transaction") ? null : allocation,
                "before_status", invalid.equals("before_status") ? "ACTIVE" : null,
                "after_status", invalid.equals("after_status") ? "ACTIVE" : null,
                "outcome", invalid.equals("outcome") ? "UNCHANGED" : "SUCCESS")));
        assertSqlState(error, "23514", "ck_admin_audit_shape");
        assertThat(committedCount()).isZero();
    }

    @Test
    void databaseRejectsOversizedNonspaceReasonRatherThanTruncating() {
        // Impossible through the validated model; exercise the existing VARCHAR with test-only SQL.
        DataAccessException error = assertThrows(DataAccessException.class, () -> caller.run(() -> fixtures.audit(
                "admin_user_id", admin, "target_account_id", target, "action", "DEMO_ALLOCATION",
                "reason", "😀".repeat(501), "transaction_id", allocation, "before_status", null, "after_status", null)));
        assertSqlState(error, "22001", "character varying(500)");
        assertThat(committedCount()).isZero();
    }

    @Test
    void mandatoryProxyRejectsMissingTransactionBeforeEnteringAdapter() {
        assertThat(AopUtils.isAopProxy(repository)).isTrue();
        assertThat(AopUtils.isAopProxy(caller)).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        // A null event would fail in the method body; MANDATORY rejects it before target invocation.
        assertThatThrownBy(() -> repository.insert(null)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> repository.insert(event())).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(committedCount()).isZero();
    }

    @Test
    void explicitGuardRejectsUnproxiedInvocationWithoutTransaction() {
        assertThatThrownBy(() -> new JdbcAdminAuditRepository(auditJdbc).insert(event()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(committedCount()).isZero();
    }

    @Test
    void explicitGuardRequiresBoundDataSourceBeyondThreadTransactionFlag() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> new JdbcAdminAuditRepository(auditJdbc).insert(event()))
                    .isInstanceOf(IllegalTransactionStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(committedCount()).isZero();
    }

    @Test
    void explicitGuardRejectsDifferentDataSourceDuringRealTransaction() throws Exception {
        var wrongResource = new JdbcAdminAuditRepository(new JdbcTemplate(isolatedDataSource()));
        assertThatThrownBy(() -> caller.run(() -> wrongResource.insert(event())))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(committedCount()).isZero();
    }

    @Test
    void explicitGuardRejectsBoundAutocommitConnection() throws SQLException {
        // Bind a real connection in the wrong mode without starting an independent transaction.
        var dataSource = auditContext.getBean(javax.sql.DataSource.class);
        try (Connection connection = database.open()) {
            assertThat(connection.getAutoCommit()).isTrue();
            TransactionSynchronizationManager.bindResource(dataSource, new ConnectionHolder(connection));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                assertThatThrownBy(() -> new JdbcAdminAuditRepository(auditJdbc).insert(event()))
                        .isInstanceOf(IllegalTransactionStateException.class);
            } finally {
                TransactionSynchronizationManager.unbindResource(dataSource);
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }
        assertThat(committedCount()).isZero();
    }

    @Test
    void rejectsReadOnlyOuterTransaction() {
        var readOnly = transaction();
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.executeWithoutResult(status -> repository.insert(event())))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(committedCount()).isZero();
    }

    @Test
    void explicitGuardAlsoRejectsActualReadOnlyConnection() {
        transaction().executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            auditJdbc.execute((ConnectionCallback<Void>) connection -> {
                connection.setReadOnly(true);
                return null;
            });
            assertThatThrownBy(() -> repository.insert(event())).isInstanceOf(IllegalTransactionStateException.class);
            status.setRollbackOnly();
        });
        assertThat(committedCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {Connection.TRANSACTION_REPEATABLE_READ, Connection.TRANSACTION_SERIALIZABLE})
    void rejectsIncompatibleActualIsolation(int isolation) {
        var wrongIsolation = transaction();
        wrongIsolation.setIsolationLevel(isolation);
        assertThatThrownBy(() -> wrongIsolation.executeWithoutResult(status -> repository.insert(event())))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(committedCount()).isZero();
    }

    @Test
    void insertIsInvisibleToIndependentCommittedReaderUntilOuterCommit() throws Exception {
        caller.run(() -> {
            assertThat(auditJdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("read committed");
            assertThat(auditJdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("off");
            repository.insert(event());
            assertThat(count(auditJdbc)).isEqualTo(1);
            try (Connection reader = database.open()) {
                assertThat(reader.getAutoCommit()).isTrue();
                JdbcTemplate observer = IsolatedTestDatabase.jdbc(reader);
                assertThat(observer.queryForObject("SELECT pg_backend_pid()", Integer.class))
                        .isNotEqualTo(auditJdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                assertThat(count(observer)).isZero();
            }
        });
        assertThat(committedCount()).isEqualTo(1);
    }

    @Test
    void explicitOuterRollbackRemovesSuccessfullyInsertedAudit() {
        transaction().executeWithoutResult(status -> {
            repository.insert(event());
            assertThat(count(auditJdbc)).isEqualTo(1);
            assertThat(committedCount()).isZero();
            status.setRollbackOnly();
        });
        assertThat(committedCount()).isZero();
    }

    @Test
    void checkedTechnicalFailureAfterInsertRollsBackThroughActualCallerProxy() {
        IOException failure = new IOException("Synthetic checked technical failure");
        assertThatThrownBy(() -> caller.run(() -> {
            repository.insert(event());
            assertThat(count(auditJdbc)).isEqualTo(1);
            throw failure;
        })).isSameAs(failure);
        assertThat(committedCount()).isZero();
    }

    @Test
    void laterSqlFailureRollsBackPreviouslyInsertedAudit() {
        DataAccessException error = assertThrows(DataAccessException.class, () -> caller.run(() -> {
            repository.insert(event());
            assertThat(count(auditJdbc)).isEqualTo(1);
            auditJdbc.queryForObject("SELECT 1 / 0", Integer.class);
        }));
        assertSqlState(error, "22012", "division by zero");
        assertThat(committedCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void auditFailureRollsBackOtherCallerWritesEvenIfCaught(boolean caught) {
        UUID marker = UUID.randomUUID();
        AdminAuditEvent valid = event();
        var failure = assertThrows(RuntimeException.class, () -> caller.run(() -> {
            fixtures.user("id", marker);
            repository.insert(valid);
            if (caught) {
                assertThrows(DataIntegrityViolationException.class, () -> repository.insert(valid));
            } else {
                repository.insert(valid);
            }
        }));
        if (caught) {
            assertThat(failure).isInstanceOf(UnexpectedRollbackException.class);
        } else {
            assertThat(failure).isInstanceOfSatisfying(DataAccessException.class,
                    error -> assertSqlState(error, "23505", "pk_admin_audit_event"));
        }
        assertThat(committedCount()).isZero();
        assertThat(auditJdbc.queryForObject("SELECT count(*) FROM app_user WHERE id = ?", Long.class, marker)).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void unexpectedAffectedRowCountFailsExplicitly(int affected) {
        // PostgreSQL INSERT normally returns one or throws. Stub only that count to cover the defensive check;
        // transaction/resource behavior above is established with real SQL, connections and proxies.
        JdbcTemplate counted = spy(auditJdbc);
        doReturn(affected).when(counted).update(anyString(), any(Object[].class));
        var adapter = new JdbcAdminAuditRepository(counted);
        assertThatThrownBy(() -> caller.run(() -> adapter.insert(event())))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("exactly one");
        assertThat(committedCount()).isZero();
    }

    private long committedCount() {
        try (Connection reader = database.open()) {
            return count(IsolatedTestDatabase.jdbc(reader));
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
    }

    private static long count(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT count(*) FROM public.admin_audit_event", Long.class);
    }

    private static void assertSqlState(DataAccessException error, String state, String message) {
        assertThat(error.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, sql -> {
            assertThat(sql.getSQLState()).isEqualTo(state);
            assertThat(sql.getMessage()).contains(message);
        });
    }
}
