package com.finfast.bootstrap.internal.application;

import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.bootstrap.internal.application.port.inbound.InitializeDatasetUseCase;
import com.finfast.bootstrap.internal.application.service.DatasetSetupWriter;
import com.finfast.bootstrap.internal.config.BootstrapConfiguration;
import com.finfast.common.money.VndAmount;
import com.finfast.identity.api.AdminProvisioning;
import com.finfast.ledger.api.GenesisDatasetInspection;
import com.finfast.ledger.api.GenesisPosting;
import com.finfast.ledger.api.PostingResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Actual Bootstrap proxies and PostgreSQL transactions over a test-owned probe table.
 * All module providers/inspection are test fixtures: no banking SQL, real Ledger inspection,
 * monetary setup, authentication, concurrent first initialization or credential evidence is claimed.
 */
@Tag("integration")
@Timeout(45)
class BootstrapTransactionBoundaryIntegrationTests {
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TREASURY = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID GENESIS = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant TIME = Instant.parse("2026-10-25T00:00:00Z");
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "postgres@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea")
            .withDatabaseName("bootstrap_boundary_test").withUsername("bootstrap_test").withPassword("bootstrap_test");
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private ProbeTransactionManager manager;
    private Probes probes;
    private AnnotationConfigApplicationContext context;
    private InitializeDatasetUseCase entry;

    @BeforeAll
    static void startIsolatedPostgres() { POSTGRES.start(); }

    @AfterAll
    static void stopIsolatedPostgres() { POSTGRES.stop(); }

    @BeforeEach
    void composeActualBootstrapProxies() throws SQLException {
        String database = "bootstrap_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
            assertThat(connection.getAutoCommit()).isTrue();
            statement.execute("CREATE DATABASE " + database);
        }
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl().replace(
                "/" + POSTGRES.getDatabaseName(), "/" + database), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE bootstrap_probe (effect VARCHAR(40) PRIMARY KEY, amount BIGINT)");
        manager = new ProbeTransactionManager(dataSource);
        manager.setEnforceReadOnly(true);
        manager.setValidateExistingTransaction(true);
        probes = new Probes(jdbc, dataSource);
        context = compose(100);
        entry = context.getBean(InitializeDatasetUseCase.class);
    }

    private AnnotationConfigApplicationContext compose(long supply) {
        var configured = new AnnotationConfigApplicationContext();
        configured.getEnvironment().getPropertySources().addFirst(new MapPropertySource("bootstrap-test", Map.of(
                "FINFAST_BOOTSTRAP_ENABLED", "true", "finfast.bootstrap.initial-supply", Long.toString(supply))));
        configured.registerBean("transactionManager", ProbeTransactionManager.class, () -> manager);
        configured.registerBean(AdminProvisioning.class, () -> probes::admin);
        configured.registerBean(TreasuryProvisioning.class, () -> probes::treasury);
        configured.registerBean(GenesisPosting.class, () -> probes::genesis);
        configured.registerBean(ProbeInspection.class, () -> new ProbeInspection(probes));
        configured.register(BootstrapConfiguration.class);
        configured.refresh();
        return configured;
    }

    @AfterEach
    void closeContextAndCheckCleanup() {
        if (context != null) context.close();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
    }

    @Test
    void requiredWriterProxyCallsAllProvidersInOrderOnOneReadCommittedTransaction() {
        assertThat(AopUtils.isAopProxy(context.getBean(DatasetSetupWriter.class))).isTrue();
        assertThat(AopUtils.isAopProxy(context.getBean(GenesisDatasetInspection.class))).isTrue();
        assertThat(AopUtils.isAopProxy(entry)).isFalse();
        entry.initializeDataset();
        assertThat(probes.events).containsExactly("inspect", "admin", "treasury", "genesis", "write:committed");
        assertThat(probes.writeTransactions).hasSize(3).containsOnly(probes.writeTransactions.getFirst());
        assertThat(probes.readTransactions).hasSize(1).doesNotContain(probes.writeTransactions.getFirst());
        assertThat(effects()).containsExactly("admin", "genesis", "treasury");
        assertThat(jdbc.queryForObject("SELECT amount FROM bootstrap_probe WHERE effect = 'genesis'", Long.class)).isEqualTo(100);
    }

    @Test
    void repeatedInvocationAndChangedSupplyNeverRepeatProvisioning() {
        entry.initializeDataset();
        var before = effects();
        context.close();
        context = compose(Long.MAX_VALUE);
        entry = context.getBean(InitializeDatasetUseCase.class);
        entry.initializeDataset();
        entry.initializeDataset();
        assertThat(effects()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT amount FROM bootstrap_probe WHERE effect = 'genesis'", Long.class)).isEqualTo(100);
        assertThat(probes.events).containsExactly("inspect", "admin", "treasury", "genesis", "write:committed", "inspect", "inspect");
    }

    @ParameterizedTest
    @EnumSource(value = Fault.class, names = {"ADMIN", "TREASURY", "GENESIS"})
    void eachProvisioningFailureRollsBackEarlierProbeWrites(Fault stage) {
        probes.fault = stage;
        assertThatThrownBy(entry::initializeDataset).isSameAs(probes.failure);
        assertThat(effects()).isEmpty();
        assertThat(probes.events.getLast()).isEqualTo("write:rolled_back");
        assertThat(probes.readTransactions).hasSize(1);
    }

    @Test
    void abortedPostgresWriteRollsBackBeforeFreshReadOnlyRepeatableReadInspection() {
        probes.fault = Fault.SQL_ABORT;
        var failure = catchThrowable(entry::initializeDataset);
        assertThat(failure).isSameAs(probes.sqlFailure).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(firstSqlState(probes.sqlFailure)).isEqualTo("23505");
        assertThat(probes.abortedSqlState).isEqualTo("25P02");
        assertThat(probes.events).containsExactly("inspect", "admin", "treasury", "write:rolled_back", "inspect");
        assertThat(probes.readTransactions).hasSize(2).doesNotContain(probes.writeTransactions.getFirst());
        assertThat(probes.readTransactions.getFirst()).isNotEqualTo(probes.readTransactions.getLast());
        assertThat(effects()).isEmpty();
    }

    @Test
    void failedRecoveryInspectionPropagatesUncertaintyAfterRollback() {
        probes.fault = Fault.SQL_ABORT;
        probes.failRecoveryRead = true;
        var failure = catchThrowable(entry::initializeDataset);
        assertThat(failure).isSameAs(probes.inspectionFailure);
        assertThat(failure.getSuppressed()).containsExactly(probes.sqlFailure);
        assertThat(probes.events).containsExactly("inspect", "admin", "treasury", "write:rolled_back", "inspect");
        assertThat(effects()).isEmpty();
    }

    @Test
    void corruptInitialInspectionNeverStartsTheWriter() {
        probes.corruptInitialRead = true;
        assertThatThrownBy(entry::initializeDataset).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("synthetic corrupt committed inspection");
        assertThat(probes.events).containsExactly("inspect");
        assertThat(effects()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ambientTransactionIsRejectedBeforeInspectionOrProviderAccess(boolean readOnly) {
        var ambient = new TransactionTemplate(manager);
        ambient.setReadOnly(readOnly);
        ambient.setIsolationLevel(readOnly ? TransactionDefinition.ISOLATION_REPEATABLE_READ
                : TransactionDefinition.ISOLATION_READ_COMMITTED);
        ambient.executeWithoutResult(status -> assertThatThrownBy(entry::initializeDataset)
                .isInstanceOf(TransactionException.class));
        assertThat(probes.events).isEmpty();
        assertThat(effects()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "compatible-but-unproxied", "read-only", "wrong-isolation"})
    void unproxiedOrIncompatibleWriterCannotPerformAnyProviderWrite(String mode) throws Exception {
        var target = (DatasetSetupWriter) ((Advised) context.getBean(DatasetSetupWriter.class)).getTargetSource().getTarget();
        if (mode.equals("none")) {
            assertThatThrownBy(target::initialize).isInstanceOf(TransactionException.class);
        } else {
            var ambient = new TransactionTemplate(manager);
            ambient.setReadOnly(mode.equals("read-only"));
            ambient.setIsolationLevel(mode.equals("wrong-isolation") || mode.equals("read-only")
                    ? TransactionDefinition.ISOLATION_REPEATABLE_READ : TransactionDefinition.ISOLATION_READ_COMMITTED);
            ambient.executeWithoutResult(status -> assertThatThrownBy(target::initialize).isInstanceOf(TransactionException.class));
        }
        assertThat(probes.events).isEmpty();
        assertThat(effects()).isEmpty();
    }

    @Test
    void rollbackOnlyCompletionIsNeverReportedAsSuccessfulSetup() {
        probes.fault = Fault.ROLLBACK_ONLY;
        assertThatThrownBy(entry::initializeDataset).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(effects()).isEmpty();
        assertThat(probes.events).containsExactly("inspect", "admin", "treasury", "genesis", "write:rolled_back");
    }

    @Test
    void checkedTechnicalFailureRollsBackTheActualWriterTransaction() {
        probes.fault = Fault.CHECKED;
        var failure = catchThrowable(entry::initializeDataset);
        assertThat(failure).hasRootCauseInstanceOf(IOException.class);
        assertThat(effects()).isEmpty();
        assertThat(probes.events.getLast()).isEqualTo("write:rolled_back");
    }

    @Test
    void beforeCommitFailureEscapesOnlyAfterRollback() {
        probes.fault = Fault.BEFORE_COMMIT;
        assertThatThrownBy(entry::initializeDataset).isSameAs(probes.failure);
        assertThat(effects()).isEmpty();
        assertThat(probes.events.getLast()).isEqualTo("write:rolled_back");
        assertThat(probes.readTransactions).hasSize(1);
    }

    @Test
    void lostAcknowledgementAfterPhysicalCommitPropagatesWithoutClaimingRollbackOrSuccess() {
        manager.failAfterWriteCommit = true;
        assertThatThrownBy(entry::initializeDataset).isInstanceOf(TransactionSystemException.class)
                .hasMessage("synthetic acknowledgement loss after probe commit");
        assertThat(effects()).containsExactly("admin", "genesis", "treasury");
        assertThat(probes.readTransactions).hasSize(1);
        assertThat(probes.events).doesNotContain("write:rolled_back");
    }

    @ParameterizedTest
    @EnumSource(value = Fault.class, names = {"NULL_ADMIN", "NULL_TREASURY", "NULL_PENDING", "MISMATCHED_PENDING"})
    void invalidRequiredProviderResultsRollBackInsteadOfReportingSuccess(Fault invalid) {
        probes.fault = invalid;
        assertThatThrownBy(entry::initializeDataset).isInstanceOfAny(NullPointerException.class, IllegalStateException.class);
        assertThat(effects()).isEmpty();
        assertThat(probes.events.getLast()).isEqualTo("write:rolled_back");
    }

    @Test
    void explicitRetryAfterRolledBackAttemptStartsAFreshAttempt() {
        probes.fault = Fault.GENESIS;
        assertThatThrownBy(entry::initializeDataset).isSameAs(probes.failure);
        assertThat(effects()).isEmpty();
        probes.fault = Fault.NONE;
        entry.initializeDataset();
        assertThat(effects()).containsExactly("admin", "genesis", "treasury");
        assertThat(probes.writeTransactions.getFirst()).isNotEqualTo(probes.writeTransactions.getLast());
        assertThat(probes.events).containsExactly("inspect", "admin", "treasury", "genesis", "write:rolled_back",
                "inspect", "admin", "treasury", "genesis", "write:committed");
    }

    private List<String> effects() {
        return jdbc.queryForList("SELECT effect FROM bootstrap_probe ORDER BY effect", String.class);
    }

    enum Fault { NONE, ADMIN, TREASURY, GENESIS, SQL_ABORT, ROLLBACK_ONLY, CHECKED, BEFORE_COMMIT,
        NULL_ADMIN, NULL_TREASURY, NULL_PENDING, MISMATCHED_PENDING }

    static class Probes {
        final JdbcTemplate jdbc;
        final DataSource dataSource;
        final List<String> events = new ArrayList<>();
        final List<Long> writeTransactions = new ArrayList<>();
        final List<Long> readTransactions = new ArrayList<>();
        final IllegalStateException failure = new IllegalStateException("synthetic provisioning failure");
        final DataAccessResourceFailureException inspectionFailure = new DataAccessResourceFailureException("synthetic unavailable inspection");
        Fault fault = Fault.NONE;
        DataAccessException sqlFailure;
        String abortedSqlState;
        boolean failRecoveryRead;
        boolean corruptInitialRead;
        Probes(JdbcTemplate jdbc, DataSource dataSource) { this.jdbc = jdbc; this.dataSource = dataSource; }

        UUID admin() {
            step("admin", null);
            if (fault == Fault.ADMIN) throw failure;
            return fault == Fault.NULL_ADMIN ? null : ADMIN;
        }

        UUID treasury() {
            step("treasury", null);
            if (fault == Fault.TREASURY) throw failure;
            if (fault == Fault.SQL_ABORT) {
                try {
                    jdbc.update("INSERT INTO bootstrap_probe (effect) VALUES ('admin')");
                } catch (DataAccessException original) {
                    sqlFailure = original;
                    try {
                        jdbc.queryForObject("SELECT 1", Integer.class);
                    } catch (DataAccessException aborted) {
                        abortedSqlState = firstSqlState(aborted);
                    }
                    throw original;
                }
                throw new AssertionError("Expected actual PostgreSQL uniqueness failure");
            }
            return fault == Fault.NULL_TREASURY ? null : TREASURY;
        }

        PostingResult.Pending genesis(UUID treasury, VndAmount amount) {
            assertThat(treasury).isEqualTo(TREASURY);
            step("genesis", amount.value());
            if (fault == Fault.GENESIS) throw failure;
            if (fault == Fault.CHECKED) return sneakyThrow(new IOException("synthetic checked provider failure"));
            if (fault == Fault.ROLLBACK_ONLY) TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            if (fault == Fault.NULL_PENDING) return null;
            return new PostingResult.Pending(GENESIS, "TEST-PROBE-ONLY", fault == Fault.MISMATCHED_PENDING
                    ? new VndAmount(amount.value() + 1) : amount, TIME);
        }

        private void step(String effect, Long amount) {
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
            boundConnection(false, Connection.TRANSACTION_READ_COMMITTED);
            assertThat(jdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("off");
            assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("read committed");
            if (effect.equals("admin")) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {
                        if (fault == Fault.BEFORE_COMMIT) throw failure;
                    }
                    @Override public void afterCompletion(int status) {
                        events.add(status == STATUS_COMMITTED ? "write:committed"
                                : status == STATUS_ROLLED_BACK ? "write:rolled_back" : "write:unknown");
                    }
                });
            }
            writeTransactions.add(jdbc.queryForObject("SELECT txid_current()", Long.class));
            events.add(effect);
            jdbc.update("INSERT INTO bootstrap_probe (effect, amount) VALUES (?, ?)", effect, amount);
        }

        Optional<GenesisDatasetInspection.InitializedDataset> inspect() {
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            boundConnection(true, Connection.TRANSACTION_REPEATABLE_READ);
            assertThat(jdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
            assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("repeatable read");
            // Any recovery read must follow the write proxy's afterCompletion callback.
            if (!writeTransactions.isEmpty()) {
                assertThat(events).containsAnyOf("write:rolled_back", "write:committed", "write:unknown");
            }
            readTransactions.add(jdbc.queryForObject("SELECT txid_current()", Long.class));
            events.add("inspect");
            var amounts = jdbc.queryForList("SELECT amount FROM bootstrap_probe WHERE effect = 'genesis'", Long.class);
            if (corruptInitialRead) throw new DataIntegrityViolationException("synthetic corrupt committed inspection");
            if (failRecoveryRead && readTransactions.size() > 1) throw inspectionFailure;
            return amounts.isEmpty() ? Optional.empty() : Optional.of(new GenesisDatasetInspection.InitializedDataset(
                    TREASURY, GENESIS, "TEST-PROBE-ONLY", new VndAmount(amounts.getFirst()), TIME));
        }

        private void boundConnection(boolean readOnly, int isolation) {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                assertThat(DataSourceUtils.isConnectionTransactional(DataSourceUtils.getTargetConnection(connection), dataSource)).isTrue();
                assertThat(connection.getAutoCommit()).isFalse();
                assertThat(connection.isReadOnly()).isEqualTo(readOnly);
                assertThat(connection.getTransactionIsolation()).isEqualTo(isolation);
                return null;
            });
        }
    }

    public static class ProbeInspection implements GenesisDatasetInspection {
        private final Probes probes;
        ProbeInspection(Probes probes) { this.probes = probes; }
        @Override
        @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
        public Optional<InitializedDataset> findCommitted() { return probes.inspect(); }
    }

    static class ProbeTransactionManager extends JdbcTransactionManager {
        boolean failAfterWriteCommit;
        ProbeTransactionManager(DataSource dataSource) { super(dataSource); }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            super.doCommit(status);
            if (!status.isReadOnly() && failAfterWriteCommit) {
                throw new TransactionSystemException("synthetic acknowledgement loss after probe commit");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T sneakyThrow(Throwable failure) throws E { throw (E) failure; }

    private static String firstSqlState(Throwable failure) {
        // pgJDBC chains the earlier constraint error as the aborted-transaction error's cause.
        // The outer SQL exception describes this query; the deepest cause describes the prior query.
        Throwable current = failure;
        while (!(current instanceof SQLException) && current.getCause() != null) current = current.getCause();
        assertThat(current).isInstanceOf(SQLException.class);
        return ((SQLException) current).getSQLState();
    }
}
