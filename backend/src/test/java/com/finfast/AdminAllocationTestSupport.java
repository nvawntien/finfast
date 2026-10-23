package com.finfast;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.administration.internal.adapter.outbound.persistence.JdbcAdminAuditRepository;
import com.finfast.administration.internal.application.usecase.AllocateDemoFundsService;
import com.finfast.administration.internal.application.dto.AllocationCommand;
import com.finfast.administration.internal.application.port.inbound.AllocateDemoFunds;
import com.finfast.common.money.VndAmount;
import com.finfast.idempotency.api.RequestOutcomes;
import com.finfast.identity.api.CurrentIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Ledger/audit proxies and disposable PostgreSQL. Identity/Accounts/Idempotency are TEST ONLY.
 * The outcome map simulates coordination/replay and observes Spring afterCommit; it has no SQL, locks,
 * fingerprint or durability. It cannot prove Minh's provider, crash recovery or concurrent idempotency.
 */
abstract class AdminAllocationTestSupport extends LedgerOrdinaryPostingTestSupport {
    protected AllocateDemoFunds service;
    protected JdbcAdminAuditRepository auditRepository;
    protected TestOutcomes testOutcomes;
    protected Probe probe;
    protected UUID adminId;

    @BeforeEach
    void wireAdministrationContribution() {
        probe = new Probe();
        // Spring treats TransactionException during beforeCommit differently from ordinary runtime failures.
        // Roll back that known pre-commit failure before JDBC cleanup restores autocommit. This configures
        // the SAME existing manager, not a second transaction. Production composition must retain it.
        ((JdbcTransactionManager) manager).setRollbackOnCommitFailure(true);
        adminId = inTransaction(() -> fixtures.user("role", "ADMIN"));
        ledgerContext.register(JdbcAdminAuditRepository.class);
        auditRepository = ledgerContext.getBean(JdbcAdminAuditRepository.class);
        ledgerContext.registerBean(TestIdentity.class, () -> new TestIdentity(adminId, ledgerJdbc, probe));
        ledgerContext.registerBean(TestAccountQueries.class, () -> new TestAccountQueries(ledgerJdbc, probe));
        ledgerContext.registerBean(TestOutcomes.class, () -> new TestOutcomes(ledgerJdbc, probe));
        ledgerContext.registerBean(FailingWrite.class, () -> new FailingWrite(ledgerJdbc));
        testOutcomes = ledgerContext.getBean(TestOutcomes.class);
        ledgerContext.registerBean(AllocateDemoFundsService.class, () -> new AllocateDemoFundsService(
                ledgerContext.getBean(CurrentIdentity.class), testOutcomes, ledgerContext.getBean(AccountQueries.class),
                allocations, event -> {
                    observe(ledgerJdbc, probe);
                    auditRepository.insert(event);
                    probe.afterAudit.run();
                }, ledgerContext.getBean(Clock.class)));
        service = ledgerContext.getBean(AllocateDemoFunds.class);
    }

    protected AllocationCommand command(long amount) {
        return new AllocationCommand(new RequestOutcomes.Key("  Exact BK-013 key 😀  "), number(destination),
                new VndAmount(amount), "  lý do 😀 e\u0301  ");
    }

    protected RequestOutcomes.Scope scope() {
        return new RequestOutcomes.Scope(adminId, RequestOutcomes.Operation.DEMO_ALLOCATION, command(25).key());
    }

    protected long auditCount() {
        return ledgerJdbc.queryForObject("SELECT count(*) FROM public.admin_audit_event", Long.class);
    }

    protected void assertRolledBack(String before) {
        assertThat(committedFinancialState()).isEqualTo(before);
        assertThat(probe.committed).isEmpty();
        assertThat(auditCount()).isZero();
        assertFinancialInvariants();
    }

    protected void failAuditInsert() {
        ledgerJdbc.execute("""
                CREATE FUNCTION public.test_fail_allocation_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'synthetic audit failure' USING ERRCODE = 'P0001'; END; $$
                """);
        ledgerJdbc.execute("""
                CREATE TRIGGER test_fail_allocation_audit AFTER INSERT ON public.admin_audit_event
                FOR EACH ROW EXECUTE FUNCTION public.test_fail_allocation_audit()
                """);
    }

    private static void observe(JdbcTemplate jdbc, Probe probe) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            assertThat(connection.getAutoCommit()).isFalse();
            assertThat(connection.isReadOnly()).isFalse();
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
            assertThat(DataSourceUtils.isConnectionTransactional(DataSourceUtils.getTargetConnection(connection), jdbc.getDataSource())).isTrue();
            return null;
        });
        probe.pids.add(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
    }

    protected static class Probe {
        final Map<RequestOutcomes.Scope, Stored> committed = new ConcurrentHashMap<>();
        final ConcurrentLinkedQueue<Integer> pids = new ConcurrentLinkedQueue<>();
        final AtomicInteger records = new AtomicInteger();
        volatile RequestOutcomes.Decision forcedDecision;
        Runnable afterAudit = () -> { };
        Runnable beforeRecord = () -> { };
    }

    protected record Stored(RequestOutcomes.CanonicalIntent intent, RequestOutcomes.FinalOutcome outcome) { }

    public static class TestIdentity implements CurrentIdentity {
        private final UUID admin;
        private final JdbcTemplate jdbc;
        private final Probe probe;

        TestIdentity(UUID admin, JdbcTemplate jdbc, Probe probe) {
            this.admin = admin;
            this.jdbc = jdbc;
            this.probe = probe;
        }

        @Override public AdminActor requireAdmin() {
            observe(jdbc, probe);
            return new AdminActor(admin);
        }

        @Override public CustomerActor requireCustomer() {
            throw new UnsupportedOperationException("No Customer identity in allocation fixture");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public static class TestAccountQueries implements AccountQueries {
        private final JdbcTemplate jdbc;
        private final Probe probe;

        TestAccountQueries(JdbcTemplate jdbc, Probe probe) {
            this.jdbc = jdbc;
            this.probe = probe;
        }

        @Override public Optional<CustomerAccount> findCustomerByNumber(AccountNumber number) {
            observe(jdbc, probe);
            return jdbc.query("""
                    SELECT id, customer_id, account_number, status, balance_vnd FROM public.account
                    WHERE account_type = 'CUSTOMER' AND account_number = ?
                    """, (row, index) -> new CustomerAccount(row.getObject("id", UUID.class), row.getObject("customer_id", UUID.class),
                    new AccountNumber(row.getString("account_number")), CustomerAccount.Status.valueOf(row.getString("status")),
                    new VndAmount(row.getLong("balance_vnd"))), number.value()).stream().findFirst();
        }

        @Override public Optional<CustomerAccount> findByCustomerId(UUID customerId) {
            throw new UnsupportedOperationException("Allocation resolves Account Number only");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public static class TestOutcomes implements RequestOutcomes {
        private final JdbcTemplate jdbc;
        private final Probe probe;

        TestOutcomes(JdbcTemplate jdbc, Probe probe) {
            this.jdbc = jdbc;
            this.probe = probe;
        }

        @Override public Decision inspect(Scope scope, CanonicalIntent intent) {
            observe(jdbc, probe);
            if (probe.forcedDecision != null) {
                return probe.forcedDecision;
            }
            Stored stored = probe.committed.get(scope);
            return stored == null ? new Execute() : stored.intent().equals(intent) ? new Replay(stored.outcome()) : new Conflict();
        }

        @Override public void record(Scope scope, CanonicalIntent intent, FinalOutcome outcome) {
            observe(jdbc, probe);
            probe.records.incrementAndGet();
            probe.beforeRecord.run();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    assertThat(probe.committed.putIfAbsent(scope, new Stored(intent, outcome))).isNull();
                }
            });
        }

        @Override public Optional<FinalOutcome> find(Scope scope) {
            throw new UnsupportedOperationException("No outcome lookup feature in Phase 3 fixture");
        }
    }

    /** Real failed SQL behind a separate MANDATORY proxy, used to exercise rollback-only propagation. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public static class FailingWrite {
        private final JdbcTemplate jdbc;

        FailingWrite(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        public void fail() {
            jdbc.execute("SELECT 1 / 0");
        }
    }
}
