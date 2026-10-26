package com.finfast;

import com.finfast.accounts.api.*;
import com.finfast.accounts.internal.adapter.outbound.persistence.JdbcAccountRepository;
import com.finfast.accounts.internal.adapter.outbound.persistence.JdbcAccountStatusRepository;
import com.finfast.accounts.internal.application.port.outbound.AccountRepositoryPort;
import com.finfast.accounts.internal.application.usecase.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Accounts providers in a fresh migrated Testcontainer database per invocation. */
abstract class AccountsPostgresTestSupport extends PostgresTestSupport {
    private static final AtomicInteger NAMES = new AtomicInteger();
    protected AnnotationConfigApplicationContext accountsContext;
    protected IsolatedTestDatabase database;
    protected DataSource source;
    protected JdbcTemplate dbJdbc;
    protected JdbcTransactionManager manager;
    protected AccountProvisioning provisioning;
    protected TreasuryProvisioning treasury;
    protected PostingAccounts posting;
    protected AccountQueries queries;
    protected AccountStatusCommands statuses;
    protected AccountRepositoryPort repository;
    protected DatabaseFixtures fixtures;

    @BeforeEach void composeAccounts() throws SQLException {
        int name = NAMES.getAndIncrement();
        database = IsolatedTestDatabase.create(postgres, "accounts_" + (char) ('a' + name / 26) + (char) ('a' + name % 26));
        source = independentSource();
        dbJdbc = new JdbcTemplate(source);
        manager = new JdbcTransactionManager(source);
        manager.setRollbackOnCommitFailure(true);
        accountsContext = new AnnotationConfigApplicationContext();
        accountsContext.registerBean(DataSource.class, () -> source);
        accountsContext.registerBean(JdbcTemplate.class, () -> dbJdbc);
        accountsContext.registerBean(JdbcTransactionManager.class, () -> manager);
        accountsContext.register(Transactions.class, JdbcAccountRepository.class, JdbcAccountStatusRepository.class,
                AccountProvisioningService.class, TreasuryProvisioningService.class, PostingAccountsService.class,
                AccountQueryService.class, AccountStatusCommandService.class);
        accountsContext.refresh();
        provisioning = accountsContext.getBean(AccountProvisioning.class);
        treasury = accountsContext.getBean(TreasuryProvisioning.class);
        posting = accountsContext.getBean(PostingAccounts.class);
        queries = accountsContext.getBean(AccountQueries.class);
        statuses = accountsContext.getBean(AccountStatusCommands.class);
        repository = accountsContext.getBean(AccountRepositoryPort.class);
        fixtures = new DatabaseFixtures(dbJdbc);
    }
    @AfterEach void closeAccounts() { if (accountsContext != null) { accountsContext.close(); } }
    protected DataSource independentSource() {
        return new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException { return database.open(); }
            @Override public Connection getConnection(String user, String password) throws SQLException {
                throw new SQLFeatureNotSupportedException("Isolated test credentials only");
            }
        };
    }
    protected TransactionTemplate transaction() { return new TransactionTemplate(manager); }
    protected <T> T inTransaction(Supplier<T> action) { return transaction().execute(status -> action.get()); }
    protected CustomerAccount customer(long balance) {
        UUID owner = fixtures.customer();
        CustomerAccount result = inTransaction(() -> provisioning.openCustomerAccount(owner));
        dbJdbc.update("UPDATE public.account SET balance_vnd = ? WHERE id = ?", balance, result.accountId()); // Fixture only.
        return queries.findByCustomerId(owner).orElseThrow();
    }
    protected long balance(UUID account) {
        return dbJdbc.queryForObject("SELECT balance_vnd FROM public.account WHERE id = ?", Long.class, account);
    }
    protected int pid() { return dbJdbc.queryForObject("SELECT pg_backend_pid()", Integer.class); }
    protected void awaitBlocked(int waiter, int blocker, Future<?> pending) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            assertThat(pending.isDone()).as("transaction must wait for the held PostgreSQL lock").isFalse();
            if (Boolean.TRUE.equals(dbJdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, blocker, waiter))) { return; }
            Thread.onSpinWait();
        }
        throw new AssertionError("Expected PostgreSQL blocking relationship was not observed");
    }
    protected static void await(CountDownLatch latch) {
        try { assertThat(latch.await(8, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
    @TestConfiguration(proxyBeanMethods = false) @EnableTransactionManagement
    static class Transactions { }
}
