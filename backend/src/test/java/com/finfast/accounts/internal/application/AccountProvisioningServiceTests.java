package com.finfast.accounts.internal.application;

import com.finfast.accounts.internal.application.usecase.AccountProvisioningService;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.common.error.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountProvisioningServiceTests {

    private InMemoryAccountRepository repository;
    private AccountProvisioningService service;
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = new InMemoryAccountRepository();
        service = new AccountProvisioningService(repository);
    }

    @Test
    void openCustomerAccountCreatesActiveZeroBalanceAccount() {
        CustomerAccount account = service.openCustomerAccount(CUSTOMER_ID);

        assertThat(account.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(account.number().value()).startsWith("ACC-");
        assertThat(account.status()).isEqualTo(CustomerAccount.Status.ACTIVE);
        assertThat(account.balance().value()).isZero();

        assertThat(repository.existsByCustomerId(CUSTOMER_ID)).isTrue();
    }

    @Test
    void openCustomerAccountRejectsDuplicateCustomerAccount() {
        service.openCustomerAccount(CUSTOMER_ID);

        assertThatThrownBy(() -> service.openCustomerAccount(CUSTOMER_ID))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).code()).isEqualTo("DUPLICATE_CUSTOMER_ACCOUNT"));
    }
}
