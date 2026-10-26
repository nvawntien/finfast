package com.finfast.accounts.internal.application;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AccountStatusCommands.Change;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.internal.application.usecase.AccountStatusCommandService;
import com.finfast.accounts.internal.domain.entity.Account;
import com.finfast.common.error.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountStatusCommandServiceTests {

    private InMemoryAccountStatusRepository repository;
    private AccountStatusCommandService service;

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final AccountNumber NUMBER = new AccountNumber("ACC-1111111111");

    @BeforeEach
    void setUp() {
        repository = new InMemoryAccountStatusRepository();
        service = new AccountStatusCommandService(repository);
    }

    @Test
    void changesStatusFromActiveToFrozen() {
        Account account = Account.openCustomer(ACCOUNT_ID, CUSTOMER_ID, NUMBER);
        repository.insert(account);

        Change change = service.changeCustomerStatus(ACCOUNT_ID, CustomerAccount.Status.FROZEN);
        assertThat(change.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(change.before()).isEqualTo(CustomerAccount.Status.ACTIVE);
        assertThat(change.after()).isEqualTo(CustomerAccount.Status.FROZEN);
        assertThat(change.changed()).isTrue();

        Account updated = repository.findById(ACCOUNT_ID);
        assertThat(updated.status()).isEqualTo(CustomerAccount.Status.FROZEN);
    }

    @Test
    void returnsUnchangedWhenDesiredStatusMatchesCurrent() {
        Account account = Account.openCustomer(ACCOUNT_ID, CUSTOMER_ID, NUMBER);
        repository.insert(account);

        Change change = service.changeCustomerStatus(ACCOUNT_ID, CustomerAccount.Status.ACTIVE);
        assertThat(change.changed()).isFalse();
        assertThat(change.before()).isEqualTo(CustomerAccount.Status.ACTIVE);
        assertThat(change.after()).isEqualTo(CustomerAccount.Status.ACTIVE);
    }

    @Test
    void rejectsUnknownAccount() {
        assertThatThrownBy(() -> service.changeCustomerStatus(UUID.randomUUID(), CustomerAccount.Status.FROZEN))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).code()).isEqualTo("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void rejectsTreasuryStatusChange() {
        UUID treasuryId = UUID.randomUUID();
        Account treasury = Account.openTreasury(treasuryId, new AccountNumber("TREASURY"));
        repository.insert(treasury);

        assertThatThrownBy(() -> service.changeCustomerStatus(treasuryId, CustomerAccount.Status.FROZEN))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).code()).isEqualTo("TREASURY_STATUS_IMMUTABLE"));
    }
}
