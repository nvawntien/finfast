package com.finfast;

import com.finfast.common.error.ApplicationException;
import com.finfast.customer.api.CustomerProvisioning;
import com.finfast.customer.internal.adapter.outbound.persistence.JdbcCustomerProfiles;
import com.finfast.customer.internal.application.usecase.ProvisionCustomer;
import com.finfast.identity.internal.adapter.outbound.persistence.JdbcRegistrationUsers;
import com.finfast.identity.internal.adapter.outbound.security.BcryptRegistrationPasswords;
import com.finfast.identity.internal.application.dto.RegistrationCommand;
import com.finfast.identity.internal.application.port.inbound.CustomerRegistration;
import com.finfast.identity.internal.application.usecase.RegisterCustomer;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataAccessException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/** Test-scoped composition of real BK-005 Identity -> Customer -> Accounts providers. No Registration HTTP is added. */
@Timeout(45)
class AccountsRegistrationIntegrationTests extends AccountsPostgresTestSupport {
    private ValidatorFactory validation;
    private CustomerProvisioning customers;
    @BeforeEach void composeRegistration() {
        validation = Validation.buildDefaultValidatorFactory();
        accountsContext.register(JdbcCustomerProfiles.class, ProvisionCustomer.class);
        customers = accountsContext.getBean(CustomerProvisioning.class);
        assertThat(AopUtils.isAopProxy(customers)).isTrue();
    }
    @AfterEach void closeValidation() { if (validation != null) { validation.close(); } }
    private CustomerRegistration registration(CustomerProvisioning provider) {
        return new RegisterCustomer(new JdbcRegistrationUsers(dbJdbc), new BcryptRegistrationPasswords(4), provider,
                validation.getValidator(), manager);
    }
    private RegistrationCommand command(String email) { return new RegistrationCommand("Synthetic Account Owner", email, "synthetic-password"); }
    private List<Long> counts() {
        return List.of("app_user", "customer", "account").stream()
                .map(table -> dbJdbc.queryForObject("SELECT count(*) FROM public." + table, Long.class)).toList();
    }
    @Test void realAccountProvisioningCommitsWithRegistrationAndDuplicateEmailHasNoPartialEffects() {
        var service = registration(customers);
        var registered = service.register(command("owner@example.test"));
        assertThat(counts()).containsExactly(1L, 1L, 1L);
        var account = queries.findByCustomerId(registered.customerId()).orElseThrow();
        assertThat(account.number().value()).isEqualTo(registered.accountNumber()).matches("ACC-[0-9]{10}");
        assertThat(account.balance().value()).isZero();
        assertThat(account.status().name()).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> service.register(command("OWNER@EXAMPLE.TEST")))
                .isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.code()).isEqualTo("EMAIL_ALREADY_REGISTERED"));
        assertThat(counts()).containsExactly(1L, 1L, 1L);
    }
    @ParameterizedTest @ValueSource(strings = {"insert", "afterAccounts", "commit"})
    void accountOrLaterTechnicalFailureNeverReportsUncommittedRegistration(String stage) {
        if (!stage.equals("afterAccounts")) {
            dbJdbc.execute("CREATE FUNCTION public.fail_registration() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Injected Accounts persistence failure'; END $$");
            String trigger = stage.equals("commit")
                    ? "CREATE CONSTRAINT TRIGGER fail_registration AFTER INSERT ON public.account DEFERRABLE INITIALLY DEFERRED"
                    : "CREATE TRIGGER fail_registration BEFORE INSERT ON public.account";
            dbJdbc.execute(trigger + " FOR EACH ROW EXECUTE FUNCTION public.fail_registration()");
        }
        CustomerProvisioning provider = stage.equals("afterAccounts") ? (user, name) -> {
            customers.provision(user, name);
            throw new IllegalStateException("Injected failure after real Accounts provisioned");
        } : customers;
        assertThatThrownBy(() -> registration(provider).register(command("owner@example.test")))
                .isInstanceOf(switch (stage) {
                    case "afterAccounts" -> IllegalStateException.class;
                    case "commit" -> org.springframework.transaction.TransactionSystemException.class;
                    default -> DataAccessException.class;
                });
        assertThat(counts()).containsExactly(0L, 0L, 0L);
    }
    @Test void concurrentRegistrationWithRealAccountsCommitsExactlyOneCompleteResult() throws Exception {
        var service = registration(customers);
        CountDownLatch start = new CountDownLatch(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var futures = List.of("owner@example.test", "OWNER@EXAMPLE.TEST").stream().map(email -> workers.submit(() -> {
                start.countDown(); await(start);
                try { service.register(command(email)); return "COMMITTED"; }
                catch (ApplicationException e) { return e.code(); }
            })).toList();
            assertThat(List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("COMMITTED", "EMAIL_ALREADY_REGISTERED");
        }
        assertThat(counts()).containsExactly(1L, 1L, 1L);
    }
}
