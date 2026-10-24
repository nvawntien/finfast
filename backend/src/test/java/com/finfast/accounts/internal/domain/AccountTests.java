package com.finfast.accounts.internal.domain;

import com.finfast.accounts.internal.domain.entity.Account;
import com.finfast.accounts.internal.domain.valueobject.AccountType;
import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.common.error.ApplicationException;
import com.finfast.common.money.VndAmount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTests {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final AccountNumber NUMBER = new AccountNumber("ACC-1234567890");
    private static final AccountNumber TREASURY_NUMBER = new AccountNumber("TREASURY");

    @Test
    void openCustomerInitializesActiveAccountWithZeroBalance() {
        Account account = Account.openCustomer(ACCOUNT_ID, CUSTOMER_ID, NUMBER);

        assertThat(account.id()).isEqualTo(ACCOUNT_ID);
        assertThat(account.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(account.number()).isEqualTo(NUMBER);
        assertThat(account.type()).isEqualTo(AccountType.CUSTOMER);
        assertThat(account.status()).isEqualTo(CustomerAccount.Status.ACTIVE);
        assertThat(account.currency()).isEqualTo("VND");
        assertThat(account.balance().value()).isZero();

        CustomerAccount snapshot = account.toCustomerAccount();
        assertThat(snapshot.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(snapshot.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(snapshot.number()).isEqualTo(NUMBER);
        assertThat(snapshot.status()).isEqualTo(CustomerAccount.Status.ACTIVE);
        assertThat(snapshot.balance().value()).isZero();
    }

    @Test
    void openTreasuryInitializesActiveTreasuryWithoutCustomerId() {
        Account treasury = Account.openTreasury(ACCOUNT_ID, TREASURY_NUMBER);

        assertThat(treasury.id()).isEqualTo(ACCOUNT_ID);
        assertThat(treasury.customerId()).isNull();
        assertThat(treasury.number()).isEqualTo(TREASURY_NUMBER);
        assertThat(treasury.type()).isEqualTo(AccountType.TREASURY);
        assertThat(treasury.status()).isEqualTo(CustomerAccount.Status.ACTIVE);
        assertThat(treasury.currency()).isEqualTo("VND");
        assertThat(treasury.balance().value()).isZero();

        assertThatThrownBy(treasury::toCustomerAccount)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Treasury account cannot be exported");
    }

    @Test
    void rejectsInvalidInvariantsOnCreation() {
        assertThatThrownBy(() -> new Account(ACCOUNT_ID, NUMBER, CUSTOMER_ID, AccountType.CUSTOMER,
                CustomerAccount.Status.ACTIVE, "USD", new VndAmount(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only VND currency is supported");

        assertThatThrownBy(() -> new Account(ACCOUNT_ID, NUMBER, null, AccountType.CUSTOMER,
                CustomerAccount.Status.ACTIVE, "VND", new VndAmount(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer Account must have an owning customerId");

        assertThatThrownBy(() -> new Account(ACCOUNT_ID, TREASURY_NUMBER, CUSTOMER_ID, AccountType.TREASURY,
                CustomerAccount.Status.ACTIVE, "VND", new VndAmount(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Treasury Account must not have an owning customerId");

        assertThatThrownBy(() -> new Account(ACCOUNT_ID, TREASURY_NUMBER, null, AccountType.TREASURY,
                CustomerAccount.Status.FROZEN, "VND", new VndAmount(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Treasury Account must remain ACTIVE");
    }

    @Test
    void debitDecreasesBalanceWhenActiveAndSufficientFunds() {
        Account account = Account.openCustomer(ACCOUNT_ID, CUSTOMER_ID, NUMBER);
        account.credit(new VndAmount(100_000));

        account.debit(new VndAmount(40_000));
        assertThat(account.balance().value()).isEqualTo(60_000);
    }

    @Test
    void debitFailsWhenInsufficientFunds() {
        Account account = Account.openCustomer(ACCOUNT_ID, CUSTOMER_ID, NUMBER);
        account.credit(new VndAmount(50_000));

        assertThatThrownBy(() -> account.debit(new VndAmount(50_001)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).code()).isEqualTo("INSUFFICIENT_FUNDS"));
    }

    @Test
    void debitFailsWhenFrozen() {
        Account account = Account.openCustomer(ACCOUNT_ID, CUSTOMER_ID, NUMBER);
        account.credit(new VndAmount(100_000));
        account.changeStatus(CustomerAccount.Status.FROZEN);

        assertThatThrownBy(() -> account.debit(new VndAmount(10_000)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).code()).isEqualTo("SOURCE_ACCOUNT_FROZEN"));
    }

    @Test
    void creditFailsWhenFrozen() {
        Account account = Account.openCustomer(ACCOUNT_ID, CUSTOMER_ID, NUMBER);
        account.changeStatus(CustomerAccount.Status.FROZEN);

        assertThatThrownBy(() -> account.credit(new VndAmount(10_000)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).code()).isEqualTo("RECIPIENT_ACCOUNT_FROZEN"));
    }

    @Test
    void changeStatusAllowsFreezingAndUnfreezingCustomer() {
        Account account = Account.openCustomer(ACCOUNT_ID, CUSTOMER_ID, NUMBER);
        account.changeStatus(CustomerAccount.Status.FROZEN);
        assertThat(account.status()).isEqualTo(CustomerAccount.Status.FROZEN);

        account.changeStatus(CustomerAccount.Status.ACTIVE);
        assertThat(account.status()).isEqualTo(CustomerAccount.Status.ACTIVE);
    }

    @Test
    void changeStatusRejectsModifyingTreasuryStatus() {
        Account treasury = Account.openTreasury(ACCOUNT_ID, TREASURY_NUMBER);

        assertThatThrownBy(() -> treasury.changeStatus(CustomerAccount.Status.FROZEN))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).code()).isEqualTo("TREASURY_STATUS_IMMUTABLE"));
    }

    @Test
    void initializeSupplySetsInitialTreasuryBalanceOnce() {
        Account treasury = Account.openTreasury(ACCOUNT_ID, TREASURY_NUMBER);
        treasury.initializeSupply(new VndAmount(100_000_000));
        assertThat(treasury.balance().value()).isEqualTo(100_000_000);

        assertThatThrownBy(() -> treasury.initializeSupply(new VndAmount(50_000_000)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).code()).isEqualTo("TREASURY_ALREADY_INITIALIZED"));
    }

    @Test
    void initializeSupplyRejectsCustomerAccount() {
        Account customer = Account.openCustomer(ACCOUNT_ID, CUSTOMER_ID, NUMBER);
        assertThatThrownBy(() -> customer.initializeSupply(new VndAmount(100_000_000)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).code()).isEqualTo("INVALID_ACCOUNT_TYPE"));
    }
}
