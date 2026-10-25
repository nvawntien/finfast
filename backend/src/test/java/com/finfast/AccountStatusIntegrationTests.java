package com.finfast;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AccountQueries;
import com.finfast.accounts.api.AccountStatusCommands;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.internal.adapter.outbound.persistence.JdbcAccountStatusRepository;
import com.finfast.accounts.internal.application.usecase.AccountStatusCommandService;
import com.finfast.administration.internal.adapter.outbound.persistence.JdbcAdminAuditRepository;
import com.finfast.administration.internal.application.dto.StatusChangeCommand;
import com.finfast.administration.internal.application.dto.StatusChangeResult;
import com.finfast.administration.internal.application.port.inbound.ChangeCustomerStatus;
import com.finfast.administration.internal.application.usecase.ChangeCustomerStatusService;
import com.finfast.common.error.ApplicationException;
import com.finfast.identity.api.CurrentIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PostgreSQL evidence for the Accounts lock and Administration-owned BK-017 transaction. */
class AccountStatusIntegrationTests extends PostgresTestSupport {
    private static final AtomicInteger DATABASE_NAMES = new AtomicInteger();
    private static final Instant NOW = Instant.parse("2026-10-24T01:02:03.123456789Z");

    private IsolatedTestDatabase database;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate statusJdbc;
    private PlatformTransactionManager manager;
    private ChangeCustomerStatus change;
    private AccountStatusCommands statuses;
    private DatabaseFixtures fixtures;
    private UUID admin;
    private UUID target;
    private AccountNumber targetNumber;

    @BeforeEach
    void compose() throws SQLException {
        int number = DATABASE_NAMES.getAndIncrement();
        String suffix = "" + (char) ('a' + number / 26) + (char) ('a' + number % 26);
        database = IsolatedTestDatabase.create(postgres, "account_status_" + suffix);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, this::dataSource);
        context.register(Transactions.class);
        context.refresh();
        statusJdbc = context.getBean(JdbcTemplate.class);
        manager = context.getBean(PlatformTransactionManager.class);
        fixtures = new DatabaseFixtures(statusJdbc);
        inTransaction(() -> {
            admin = fixtures.user("role", "ADMIN");
            target = fixtures.account();
            targetNumber = new AccountNumber(statusJdbc.queryForObject(
                    "SELECT account_number FROM public.account WHERE id = ?", String.class, target));
            return null;
        });

        context.register(JdbcAccountStatusRepository.class, AccountStatusCommandService.class,
                JdbcAdminAuditRepository.class);
        context.registerBean(CurrentIdentity.class, () -> new TestIdentity(admin));
        context.registerBean(AccountQueries.class, () -> new TestAccountQueries(statusJdbc));
        context.registerBean(ChangeCustomerStatusService.class, () -> new ChangeCustomerStatusService(
                context.getBean(CurrentIdentity.class), context.getBean(AccountQueries.class),
                context.getBean(AccountStatusCommands.class), context.getBean(JdbcAdminAuditRepository.class),
                Clock.fixed(NOW, ZoneOffset.UTC)));
        statuses = context.getBean(AccountStatusCommands.class);
        change = context.getBean(ChangeCustomerStatus.class);
        ((JdbcTransactionManager) manager).setRollbackOnCommitFailure(true);
    }

    @AfterEach
    void close() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void freezesCustomerAndAuditsTheEffectiveTransitionAtomically() {
        var result = change.change(new StatusChangeCommand(targetNumber, CustomerAccount.Status.FROZEN, "risk review"));

        assertThat(result).isEqualTo(new StatusChangeResult.Changed(CustomerAccount.Status.ACTIVE,
                CustomerAccount.Status.FROZEN));
        assertThat(status("status")).isEqualTo("FROZEN");
        assertThat(statusJdbc.queryForObject("SELECT action FROM public.admin_audit_event", String.class))
                .isEqualTo("FREEZE_ACCOUNT");
        assertThat(statusJdbc.queryForObject("SELECT outcome FROM public.admin_audit_event", String.class))
                .isEqualTo("SUCCESS");
        assertThat(statusJdbc.queryForObject("SELECT before_status FROM public.admin_audit_event", String.class))
                .isEqualTo("ACTIVE");
        assertThat(statusJdbc.queryForObject("SELECT after_status FROM public.admin_audit_event", String.class))
                .isEqualTo("FROZEN");
        assertThat(statusJdbc.queryForObject("SELECT transaction_id FROM public.admin_audit_event", UUID.class))
                .isNull();
    }

    @Test
    void sameStatusReturnsUnchangedWithoutARequiredSuccessAudit() {
        var result = change.change(new StatusChangeCommand(targetNumber, CustomerAccount.Status.ACTIVE, "review"));

        assertThat(result).isEqualTo(new StatusChangeResult.Unchanged(CustomerAccount.Status.ACTIVE));
        assertThat(status("status")).isEqualTo("ACTIVE");
        assertThat(auditCount()).isZero();
    }

    @Test
    void unfreezesCustomerWithTheOppositeImmutableAudit() {
        statusJdbc.update("UPDATE public.account SET status = 'FROZEN' WHERE id = ?", target);

        var result = change.change(new StatusChangeCommand(targetNumber, CustomerAccount.Status.ACTIVE, "appeal approved"));

        assertThat(result).isEqualTo(new StatusChangeResult.Changed(CustomerAccount.Status.FROZEN,
                CustomerAccount.Status.ACTIVE));
        assertThat(status("status")).isEqualTo("ACTIVE");
        assertThat(statusJdbc.queryForObject("SELECT action FROM public.admin_audit_event", String.class))
                .isEqualTo("UNFREEZE_ACCOUNT");
        assertThat(statusJdbc.queryForObject("SELECT before_status FROM public.admin_audit_event", String.class))
                .isEqualTo("FROZEN");
        assertThat(statusJdbc.queryForObject("SELECT after_status FROM public.admin_audit_event", String.class))
                .isEqualTo("ACTIVE");
    }

    @Test
    void auditFailureRollsBackTheStatusChange() {
        statusJdbc.execute("""
                CREATE FUNCTION public.test_fail_status_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'synthetic status audit failure' USING ERRCODE = 'P0001'; END; $$
                """);
        statusJdbc.execute("""
                CREATE TRIGGER test_fail_status_audit AFTER INSERT ON public.admin_audit_event
                FOR EACH ROW WHEN (NEW.action IN ('FREEZE_ACCOUNT', 'UNFREEZE_ACCOUNT'))
                EXECUTE FUNCTION public.test_fail_status_audit()
                """);

        assertThatThrownBy(() -> change.change(new StatusChangeCommand(targetNumber,
                CustomerAccount.Status.FROZEN, "audit failure"))).isInstanceOf(RuntimeException.class);

        assertThat(status("status")).isEqualTo("ACTIVE");
        assertThat(auditCount()).isZero();
    }

    @Test
    void rejectsBlankReasonBeforeAnyStatusMutation() {
        assertThatThrownBy(() -> change.change(new StatusChangeCommand(targetNumber,
                CustomerAccount.Status.FROZEN, "  ")))
                .isInstanceOf(ApplicationException.class)
                .extracting("code").isEqualTo("REASON_REQUIRED");

        assertThat(status("status")).isEqualTo("ACTIVE");
        assertThat(auditCount()).isZero();
    }

    @Test
    void excludesTreasuryFromCustomerStatusCapability() {
        UUID treasury = inTransaction(() -> fixtures.account("account_type", "TREASURY", "customer_id", null));

        assertThatThrownBy(() -> inTransaction(() -> statuses.changeCustomerStatus(treasury,
                CustomerAccount.Status.FROZEN))).isInstanceOf(ApplicationException.class)
                .extracting("code").isEqualTo("TREASURY_STATUS_IMMUTABLE");
        assertThat(statusJdbc.queryForObject("SELECT status FROM public.account WHERE id = ?", String.class, treasury))
                .isEqualTo("ACTIVE");
    }

    @Test
    void statusProviderRequiresCallerOwnedWriteTransaction() {
        assertThatThrownBy(() -> statuses.changeCustomerStatus(target, CustomerAccount.Status.FROZEN))
                .isInstanceOf(IllegalTransactionStateException.class);

        TransactionTemplate readOnly = transaction();
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.execute(status -> statuses.changeCustomerStatus(target,
                CustomerAccount.Status.FROZEN))).isInstanceOf(IllegalTransactionStateException.class);
    }

    private String status(String column) {
        return statusJdbc.queryForObject("SELECT " + column + " FROM public.account WHERE id = ?", String.class, target);
    }

    private long auditCount() {
        return statusJdbc.queryForObject("SELECT count(*) FROM public.admin_audit_event", Long.class);
    }

    private DataSource dataSource() {
        return new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException { return database.open(); }
            @Override public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLFeatureNotSupportedException("Fixture uses only container credentials");
            }
        };
    }

    private TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(12);
        return template;
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return transaction().execute(status -> work.get());
    }

    private static final class TestIdentity implements CurrentIdentity {
        private final UUID admin;

        private TestIdentity(UUID admin) { this.admin = admin; }
        @Override public CustomerActor requireCustomer() { throw new UnsupportedOperationException(); }
        @Override public AdminActor requireAdmin() { return new AdminActor(admin); }
    }

    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public static class TestAccountQueries implements AccountQueries {
        private final JdbcTemplate jdbc;

        public TestAccountQueries(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Override public Optional<CustomerAccount> findCustomerByNumber(AccountNumber number) {
            return jdbc.query("""
                    SELECT id, customer_id, account_number, status, balance_vnd
                    FROM public.account
                    WHERE account_type = 'CUSTOMER' AND account_number = ?
                    """, (row, index) -> new CustomerAccount(row.getObject("id", UUID.class),
                    row.getObject("customer_id", UUID.class), new AccountNumber(row.getString("account_number")),
                    CustomerAccount.Status.valueOf(row.getString("status")),
                    new com.finfast.common.money.VndAmount(row.getLong("balance_vnd"))), number.value())
                    .stream().findFirst();
        }

        @Override public Optional<CustomerAccount> findByCustomerId(UUID customerId) {
            throw new UnsupportedOperationException("BK-017 fixture resolves by Account Number");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }
    }
}
