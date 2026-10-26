package com.finfast;

import com.finfast.accounts.api.*;
import com.finfast.accounts.internal.adapter.outbound.persistence.JdbcAccountRepository;
import com.finfast.accounts.internal.domain.entity.Account;
import com.finfast.common.error.ApplicationException;
import com.finfast.common.money.VndAmount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(45)
class AccountsPersistenceIntegrationTests extends AccountsPostgresTestSupport {
    @Test void provisionsExactlyOneGeneratedZeroCustomerAccountAndExcludesTreasury() {
        UUID owner = fixtures.customer();
        var opened = inTransaction(() -> provisioning.openCustomerAccount(owner));
        assertThat(opened.customerId()).isEqualTo(owner);
        assertThat(opened.number().value()).matches("ACC-[0-9]{10}");
        assertThat(opened.status()).isEqualTo(CustomerAccount.Status.ACTIVE);
        assertThat(opened.balance().value()).isZero();
        assertThat(dbJdbc.queryForMap("SELECT account_type, currency, status, balance_vnd FROM public.account WHERE id = ?", opened.accountId()))
                .containsEntry("account_type", "CUSTOMER").containsEntry("currency", "VND")
                .containsEntry("status", "ACTIVE").containsEntry("balance_vnd", 0L);
        assertThat(queries.findByCustomerId(owner)).contains(opened);
        assertThatThrownBy(() -> inTransaction(() -> provisioning.openCustomerAccount(owner)))
                .isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.code()).isEqualTo("DUPLICATE_CUSTOMER_ACCOUNT"));
        UUID treasuryId = inTransaction(treasury::openTreasury);
        assertThat(queries.findCustomerByNumber(new AccountNumber("TREASURY"))).isEmpty();
        assertThat(queries.findByCustomerId(treasuryId)).isEmpty();
        assertThat(dbJdbc.queryForObject("SELECT count(*) FROM public.account WHERE customer_id = ?", Long.class, owner)).isEqualTo(1);
    }
    @Test void databaseEnforcesCustomerAndNumberUniqueness() {
        var opened = customer(0);
        UUID other = fixtures.customer();
        assertThatThrownBy(() -> inTransaction(() -> {
            repository.insert(Account.openCustomer(UUID.randomUUID(), opened.customerId(), new AccountNumber("DIFFERENT"))); return null;
        })).isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("uq_account_customer");
        assertThatThrownBy(() -> inTransaction(() -> {
            repository.insert(Account.openCustomer(UUID.randomUUID(), other, opened.number())); return null;
        })).isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("uq_account_number");
        assertThat(dbJdbc.queryForObject("SELECT count(*) FROM public.account", Long.class)).isEqualTo(1);
    }
    @Test void treasuryIsZeroActiveVndSingletonAndDatabaseRejectsSecond() {
        UUID id = inTransaction(treasury::openTreasury);
        assertThat(dbJdbc.queryForMap("SELECT customer_id, account_type, status, currency, balance_vnd FROM public.account WHERE id = ?", id))
                .containsEntry("customer_id", null).containsEntry("account_type", "TREASURY")
                .containsEntry("status", "ACTIVE").containsEntry("currency", "VND").containsEntry("balance_vnd", 0L);
        assertThatThrownBy(() -> inTransaction(treasury::openTreasury)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> inTransaction(() -> {
            repository.insert(Account.openTreasury(UUID.randomUUID(), new AccountNumber("SECOND-TREASURY"))); return null;
        })).isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("uq_account_single_treasury");
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void concurrentProvisioningHasOnlyOneCommittedWinner(boolean provisionTreasury) throws Exception {
        UUID owner = fixtures.customer();
        CountDownLatch start = new CountDownLatch(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var futures = List.of(1, 2).stream().map(i -> workers.submit(() -> {
                start.countDown(); await(start);
                try {
                    inTransaction(() -> provisionTreasury ? treasury.openTreasury() : provisioning.openCustomerAccount(owner));
                    return "COMMITTED";
                } catch (ApplicationException | DataIntegrityViolationException expected) { return "DENIED"; }
            })).toList();
            assertThat(List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("COMMITTED", "DENIED");
        }
        assertThat(dbJdbc.queryForObject("SELECT count(*) FROM public.account", Long.class)).isEqualTo(1);
    }
    @Test void provisioningAndEarlierOuterOperationsRollBackTogether() {
        assertThatThrownBy(() -> inTransaction(() -> {
            UUID owner = fixtures.customer();
            provisioning.openCustomerAccount(owner);
            treasury.openTreasury();
            throw new IllegalStateException("Injected later failure");
        })).isInstanceOf(IllegalStateException.class);
        for (String table : List.of("app_user", "customer", "account")) {
            assertThat(dbJdbc.queryForObject("SELECT count(*) FROM public." + table, Long.class)).isZero();
        }
    }
    @Test void suppressedInsertIsTechnicalFailureRatherThanUnpersistedSuccess() {
        dbJdbc.execute("CREATE FUNCTION public.suppress_account_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NULL; END $$");
        dbJdbc.execute("CREATE TRIGGER suppress_insert BEFORE INSERT ON public.account FOR EACH ROW EXECUTE FUNCTION public.suppress_account_insert()");
        assertThatThrownBy(() -> inTransaction(() -> provisioning.openCustomerAccount(fixtures.customer())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(dbJdbc.queryForObject("SELECT count(*) FROM public.customer", Long.class)).isZero();
        assertThat(dbJdbc.queryForObject("SELECT count(*) FROM public.account", Long.class)).isZero();
    }
    @Test void actualSpringProxiesAndMandatoryEntriesRejectMissingOuterTransaction() {
        for (Object bean : List.of(provisioning, treasury, posting, queries, statuses, repository)) {
            assertThat(AopUtils.isAopProxy(bean)).as(bean.getClass().getName()).isTrue();
        }
        assertThat(accountsContext.getBeansOfType(AccountProvisioning.class)).hasSize(1);
        assertThat(accountsContext.getBeansOfType(TreasuryProvisioning.class)).hasSize(1);
        assertThat(accountsContext.getBeansOfType(PostingAccounts.class)).hasSize(1);
        assertThat(accountsContext.getBeansOfType(AccountQueries.class)).hasSize(1);
        assertThat(accountsContext.getBeansOfType(AccountStatusCommands.class)).hasSize(1);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> provisioning.openCustomerAccount(id)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(treasury::openTreasury).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> posting.transfer(id, id, new AccountNumber("RECIPIENT"), new VndAmount(1))).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> posting.allocate(new AccountNumber("RECIPIENT"), new VndAmount(1))).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> posting.initializeTreasury(id, new VndAmount(1))).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> statuses.changeCustomerStatus(id, CustomerAccount.Status.FROZEN)).isInstanceOf(IllegalTransactionStateException.class);
    }
    @Test void actualJdbcConnectionIsBoundAndUncommittedWritesAreInvisible() {
        UUID owner = fixtures.customer();
        var opened = inTransaction(() -> {
            var bound = DataSourceUtils.getConnection(source);
            assertThat(DataSourceUtils.isConnectionTransactional(bound, source)).isTrue();
            var result = provisioning.openCustomerAccount(owner);
            assertThat(DataSourceUtils.getConnection(source)).isSameAs(bound);
            assertThat(dbJdbc.queryForObject("SELECT count(*) FROM public.account", Long.class)).isEqualTo(1);
            assertThat(new JdbcTemplate(independentSource()).queryForObject("SELECT count(*) FROM public.account", Long.class)).isZero();
            return result;
        });
        assertThat(queries.findByCustomerId(owner)).contains(opened);
    }
    @ParameterizedTest @ValueSource(strings = {"readonly", "isolation", "resource", "unbound"})
    void persistenceRefusesIncompatibleTransactions(String mode) {
        var adapter = new JdbcAccountRepository(new JdbcTemplate(mode.equals("resource") ? independentSource() : source));
        UUID owner = fixtures.customer();
        var account = Account.openCustomer(UUID.randomUUID(), owner, new AccountNumber("GUARDED"));
        var tx = transaction();
        if (mode.equals("readonly")) { tx.setReadOnly(true); }
        if (mode.equals("isolation")) { tx.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE); }
        if (mode.equals("unbound")) {
            assertThatThrownBy(() -> adapter.insert(account)).isInstanceOf(IllegalTransactionStateException.class);
            assertThatThrownBy(() -> adapter.lockById(account.id())).isInstanceOf(IllegalTransactionStateException.class);
            assertThatThrownBy(() -> adapter.updateBalance(account.id(), new VndAmount(1))).isInstanceOf(IllegalTransactionStateException.class);
            assertThatThrownBy(() -> adapter.updateStatus(account.id(), CustomerAccount.Status.FROZEN)).isInstanceOf(IllegalTransactionStateException.class);
        } else {
            assertThatThrownBy(() -> tx.execute(status -> { adapter.insert(account); return null; })).isInstanceOf(IllegalTransactionStateException.class);
        }
        assertThat(dbJdbc.queryForObject("SELECT count(*) FROM public.account", Long.class)).isZero();
    }
    @Test void zeroRowBalanceAndStatusUpdatesFailTechnically() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> inTransaction(() -> { repository.updateBalance(missing, new VndAmount(1)); return null; }))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> inTransaction(() -> { repository.updateStatus(missing, CustomerAccount.Status.FROZEN); return null; }))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void persistenceErrorsCannotBecomeEmptyCustomerRead() {
        dbJdbc.execute("ALTER TABLE public.account RENAME TO unavailable_account");
        assertThatThrownBy(() -> queries.findByCustomerId(UUID.randomUUID())).isInstanceOf(DataAccessException.class);
    }
}
