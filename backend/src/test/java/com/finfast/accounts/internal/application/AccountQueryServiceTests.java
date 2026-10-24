package com.finfast.accounts.internal.application;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.internal.application.usecase.AccountQueryService;
import com.finfast.accounts.internal.domain.entity.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountQueryServiceTests {

    private InMemoryAccountRepository repository;
    private AccountQueryService service;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final AccountNumber NUMBER = new AccountNumber("ACC-0000000001");

    @BeforeEach
    void setUp() {
        repository = new InMemoryAccountRepository();
        service = new AccountQueryService(repository);
    }

    @Test
    void findByCustomerIdReturnsCustomerAccountSnapshot() {
        Account account = Account.openCustomer(ACCOUNT_ID, CUSTOMER_ID, NUMBER);
        repository.insert(account);

        Optional<CustomerAccount> found = service.findByCustomerId(CUSTOMER_ID);
        assertThat(found).isPresent();
        assertThat(found.get().accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(found.get().customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(found.get().number()).isEqualTo(NUMBER);
        assertThat(found.get().status()).isEqualTo(CustomerAccount.Status.ACTIVE);
    }

    @Test
    void findByCustomerIdSafelyExcludesTreasury() {
        UUID treasuryId = UUID.randomUUID();
        Account treasury = Account.openTreasury(treasuryId, new AccountNumber("TREASURY"));
        repository.insert(treasury);

        // Treasury has no customerId, searching any customerId does not return Treasury
        assertThat(service.findByCustomerId(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void findCustomerByNumberReturnsCustomerAccount() {
        Account account = Account.openCustomer(ACCOUNT_ID, CUSTOMER_ID, NUMBER);
        repository.insert(account);

        Optional<CustomerAccount> found = service.findCustomerByNumber(NUMBER);
        assertThat(found).isPresent();
        assertThat(found.get().accountId()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void findCustomerByNumberSafelyExcludesTreasury() {
        AccountNumber treasuryNumber = new AccountNumber("TREASURY");
        Account treasury = Account.openTreasury(UUID.randomUUID(), treasuryNumber);
        repository.insert(treasury);

        assertThat(service.findCustomerByNumber(treasuryNumber)).isEmpty();
    }
}
