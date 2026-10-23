package com.finfast;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AccountProvisioning;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.common.error.ApplicationException;
import com.finfast.common.money.VndAmount;
import com.finfast.customer.api.CustomerProvisioning;
import com.finfast.customer.internal.adapter.outbound.persistence.JdbcCustomerProfiles;
import com.finfast.customer.internal.application.usecase.ProvisionCustomer;
import com.finfast.identity.internal.adapter.outbound.persistence.JdbcRegistrationUsers;
import com.finfast.identity.internal.adapter.outbound.security.BcryptRegistrationPasswords;
import com.finfast.identity.internal.application.port.outbound.RegistrationUsers;
import com.finfast.identity.internal.application.usecase.RegisterCustomer;
import com.finfast.identity.internal.application.usecase.RegistrationCommand;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Identity/Customer adapters and PostgreSQL; Accounts SQL below is explicitly a test-only collaborator. */
@Timeout(45)
class RegistrationIntegrationTests extends PostgresTestSupport {
    private static final AtomicInteger NAMES = new AtomicInteger();
    private IsolatedTestDatabase database;
    private DataSource source;
    private JdbcTemplate jdbc;
    private JdbcTransactionManager manager;
    private ValidatorFactory validation;
    private JdbcRegistrationUsers users;
    private JdbcCustomerProfiles profiles;

    @BeforeEach
    void database() throws SQLException {
        int number = NAMES.getAndIncrement();
        database = IsolatedTestDatabase.create(postgres, "registration_" + (char) ('a' + number / 26) + (char) ('a' + number % 26));
        source = isolatedSource();
        jdbc = new JdbcTemplate(source);
        manager = new JdbcTransactionManager(source);
        users = new JdbcRegistrationUsers(jdbc);
        profiles = new JdbcCustomerProfiles(jdbc);
        validation = Validation.buildDefaultValidatorFactory();
    }

    @AfterEach void closeValidation() { if (validation != null) { validation.close(); } }

    private DataSource isolatedSource() {
        return new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException { return database.open(); }
            @Override public Connection getConnection(String user, String password) throws SQLException {
                throw new SQLFeatureNotSupportedException("Only isolated test credentials are allowed");
            }
        };
    }

    private RegisterCustomer registration(AccountProvisioning accounts) {
        return service(users, new ProvisionCustomer(profiles, accounts), manager);
    }

    private RegisterCustomer service(RegistrationUsers store, CustomerProvisioning customers, PlatformTransactionManager transactions) {
        return new RegisterCustomer(store, new BcryptRegistrationPasswords(4), customers, validation.getValidator(), transactions);
    }

    private RegistrationCommand command(String email) { return new RegistrationCommand("Synthetic Alice", email, "synthetic-password"); }

    private CustomerAccount accountFixture(UUID customer) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        UUID account = UUID.randomUUID();
        String number = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO account (id, account_number, customer_id, account_type) VALUES (?, ?, ?, 'CUSTOMER')",
                account, number, customer);
        return new CustomerAccount(account, customer, new AccountNumber(number), CustomerAccount.Status.ACTIVE, new VndAmount(0));
    }

    private List<Long> counts() {
        return List.of("app_user", "customer", "account", "financial_transaction", "ledger_entry").stream()
                .map(table -> jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).toList();
    }

    @Test
    void commitsCustomerProfileAndExactlyOneZeroAccountBeforeReturning() {
        var result = registration(customer -> {
            // This separate connection cannot see the uncommitted User/profile writes.
            assertThat(new JdbcTemplate(isolatedSource()).queryForObject("SELECT count(*) FROM app_user", Long.class)).isZero();
            return accountFixture(customer);
        }).register(command("ALICE@EXAMPLE.TEST"));
        assertThat(counts()).containsExactly(1L, 1L, 1L, 0L, 0L);
        assertThat(jdbc.queryForObject("SELECT email_normalized FROM app_user", String.class)).isEqualTo("alice@example.test");
        assertThat(jdbc.queryForObject("SELECT role FROM app_user", String.class)).isEqualTo("CUSTOMER");
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user", String.class);
        assertThat(new BCryptPasswordEncoder().matches("synthetic-password", hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT display_name FROM customer WHERE id = ?", String.class, result.customerId())).isEqualTo("Synthetic Alice");
        assertThat(jdbc.queryForObject("SELECT account_number FROM account WHERE customer_id = ?", String.class, result.customerId())).isEqualTo(result.accountNumber());
        assertThat(jdbc.queryForMap("SELECT account_type, status, currency, balance_vnd FROM account"))
                .containsEntry("account_type", "CUSTOMER").containsEntry("status", "ACTIVE")
                .containsEntry("currency", "VND").containsEntry("balance_vnd", 0L);
    }

    @Test
    void caseVariantDuplicateHasNoExtraProfileOrAccount() {
        var register = registration(this::accountFixture);
        register.register(command("ALICE@example.test"));
        assertThatThrownBy(() -> register.register(command("alice@EXAMPLE.TEST")))
                .isInstanceOfSatisfying(ApplicationException.class, error -> assertThat(error.code()).isEqualTo("EMAIL_ALREADY_REGISTERED"));
        assertThat(counts()).containsExactly(1L, 1L, 1L, 0L, 0L);
    }

    @Test
    void concurrentCaseVariantsCreateOnlyOneCompleteRegistration() throws Exception {
        CountDownLatch atInsert = new CountDownLatch(2);
        RegistrationUsers racing = (id, email, hash) -> {
            atInsert.countDown();
            try { assertThat(atInsert.await(10, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            users.insertCustomer(id, email, hash);
        };
        var register = service(racing, new ProvisionCustomer(profiles, this::accountFixture), manager);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of("Alice@example.test", "ALICE@EXAMPLE.TEST").stream().map(email -> executor.submit(() -> {
                try { register.register(command(email)); return "CREATED"; }
                catch (ApplicationException error) { return error.code(); }
            })).toList();
            assertThat(List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED", "EMAIL_ALREADY_REGISTERED");
        }
        assertThat(counts()).containsExactly(1L, 1L, 1L, 0L, 0L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "profile", "account"})
    void failureAtEachProvisioningStageRollsEverythingBack(String stage) {
        RegistrationUsers store = (id, email, hash) -> {
            users.insertCustomer(id, email, hash);
            if (stage.equals("user")) { throw new IllegalStateException("Injected user stage failure"); }
        };
        AccountProvisioning accounts = customer -> {
            if (stage.equals("account")) { accountFixture(customer); }
            throw new IllegalStateException("Injected provisioning failure");
        };
        assertThatThrownBy(() -> service(store, new ProvisionCustomer(profiles, accounts), manager)
                .register(command("alice@example.test"))).isInstanceOf(IllegalStateException.class);
        assertThat(counts()).containsExactly(0L, 0L, 0L, 0L, 0L);
        // A technical failure does not reserve the email as a permanent rejection.
        registration(this::accountFixture).register(command("alice@example.test"));
        assertThat(counts()).containsExactly(1L, 1L, 1L, 0L, 0L);
    }

    @Test
    void databaseRejectingProfileAlsoRollsBackUser() {
        var failingProfiles = new JdbcCustomerProfiles(jdbc) ;
        CustomerProvisioning customers = (user, name) -> {
            failingProfiles.insert(UUID.randomUUID(), user, name);
            failingProfiles.insert(UUID.randomUUID(), user, name); // Unique user_id violation.
            throw new AssertionError("Database should reject second profile");
        };
        assertThatThrownBy(() -> service(users, customers, manager).register(command("alice@example.test")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(counts()).containsExactly(0L, 0L, 0L, 0L, 0L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "frozen", "funded", "missing"})
    void invalidAccountProjectionAbortsRegistration(String mode) {
        assertThatThrownBy(() -> registration(customer -> {
            CustomerAccount account = accountFixture(customer);
            if (mode.equals("missing")) { return null; }
            return new CustomerAccount(account.accountId(), mode.equals("owner") ? UUID.randomUUID() : customer,
                    account.number(), mode.equals("frozen") ? CustomerAccount.Status.FROZEN : CustomerAccount.Status.ACTIVE,
                    new VndAmount(mode.equals("funded") ? 1 : 0));
        }).register(command("alice@example.test"))).isInstanceOf(RuntimeException.class);
        assertThat(counts()).containsExactly(0L, 0L, 0L, 0L, 0L);
    }

    @Test
    void commitFailureIsNotReportedAsRegistrationSuccess() {
        var failingManager = new JdbcTransactionManager(source) {
            @Override protected void doCommit(DefaultTransactionStatus status) {
                throw new TransactionSystemException("Injected commit failure before durable commit");
            }
        };
        failingManager.setRollbackOnCommitFailure(true);
        assertThatThrownBy(() -> service(users, new ProvisionCustomer(profiles, this::accountFixture), failingManager)
                .register(command("alice@example.test"))).isInstanceOf(TransactionSystemException.class);
        assertThat(counts()).containsExactly(0L, 0L, 0L, 0L, 0L);
    }

    @Test
    void deferredCommitValidationFailureIsNotReportedAsSuccess() {
        assertThatThrownBy(() -> registration(customer -> {
            CustomerAccount result = accountFixture(customer);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("Injected precommit failure"); }
            });
            return result;
        }).register(command("alice@example.test"))).isInstanceOf(IllegalStateException.class);
        assertThat(counts()).containsExactly(0L, 0L, 0L, 0L, 0L);
    }

    @Test
    void callerTransactionCannotObservePrematureSuccess() {
        var outer = new TransactionTemplate(manager);
        assertThatThrownBy(() -> outer.execute(status -> registration(this::accountFixture).register(command("alice@example.test"))))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(counts()).containsExactly(0L, 0L, 0L, 0L, 0L);
    }

    @Test
    void persistenceAndProvisioningRefuseUnboundTransactions() {
        assertThatThrownBy(() -> users.insertCustomer(UUID.randomUUID(), "alice@example.test", "test-hash"))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> profiles.insert(UUID.randomUUID(), UUID.randomUUID(), "Alice"))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> new ProvisionCustomer(profiles, this::accountFixture).provision(UUID.randomUUID(), "Alice"))
                .isInstanceOf(IllegalTransactionStateException.class);
        var wrongResource = new JdbcRegistrationUsers(new JdbcTemplate(isolatedSource()));
        assertThatThrownBy(() -> service(wrongResource, new ProvisionCustomer(profiles, this::accountFixture), manager)
                .register(command("alice@example.test"))).isInstanceOf(IllegalTransactionStateException.class);
        var wrongProfiles = new JdbcCustomerProfiles(new JdbcTemplate(isolatedSource()));
        assertThatThrownBy(() -> service(users, new ProvisionCustomer(wrongProfiles, this::accountFixture), manager)
                .register(command("alice@example.test"))).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(counts()).containsExactly(0L, 0L, 0L, 0L, 0L);
    }

    @Test
    void readOnlyAndWrongIsolationTransactionsAreRejected() {
        var transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        assertThatThrownBy(() -> transaction.execute(status -> {
            users.insertCustomer(UUID.randomUUID(), "alice@example.test", "test-hash"); return null;
        })).isInstanceOf(IllegalTransactionStateException.class);
        transaction.setReadOnly(false);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        assertThatThrownBy(() -> transaction.execute(status -> {
            users.insertCustomer(UUID.randomUUID(), "alice@example.test", "test-hash"); return null;
        })).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(counts()).containsExactly(0L, 0L, 0L, 0L, 0L);
    }
}
