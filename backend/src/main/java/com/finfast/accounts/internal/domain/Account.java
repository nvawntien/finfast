package com.finfast.accounts.internal.domain;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.common.error.ApplicationException;
import com.finfast.common.money.VndAmount;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain model representing a FinFast Account (CUSTOMER or TREASURY).
 * Enforces all database invariants and domain rules (BR-ACC-01..06).
 */
public final class Account {

    public static final String CURRENCY_VND = "VND";

    private final UUID id;
    private final AccountNumber number;
    private final UUID customerId;
    private final AccountType type;
    private CustomerAccount.Status status;
    private final String currency;
    private VndAmount balance;

    public Account(UUID id,
                   AccountNumber number,
                   UUID customerId,
                   AccountType type,
                   CustomerAccount.Status status,
                   String currency,
                   VndAmount balance) {
        this.id = Objects.requireNonNull(id, "id");
        this.number = Objects.requireNonNull(number, "number");
        this.type = Objects.requireNonNull(type, "type");
        this.status = Objects.requireNonNull(status, "status");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.balance = Objects.requireNonNull(balance, "balance");

        if (!CURRENCY_VND.equals(currency)) {
            throw new IllegalArgumentException("Only VND currency is supported in FinFast V1");
        }
        if (type == AccountType.CUSTOMER && customerId == null) {
            throw new IllegalArgumentException("Customer Account must have an owning customerId");
        }
        if (type == AccountType.TREASURY && customerId != null) {
            throw new IllegalArgumentException("Treasury Account must not have an owning customerId");
        }
        if (type == AccountType.TREASURY && status != CustomerAccount.Status.ACTIVE) {
            throw new IllegalArgumentException("Treasury Account must remain ACTIVE");
        }
        this.customerId = customerId;
    }

    public static Account openCustomer(UUID id, UUID customerId, AccountNumber number) {
        return new Account(id, number, customerId, AccountType.CUSTOMER, CustomerAccount.Status.ACTIVE,
                CURRENCY_VND, new VndAmount(0));
    }

    public static Account openTreasury(UUID id, AccountNumber number) {
        return new Account(id, number, null, AccountType.TREASURY, CustomerAccount.Status.ACTIVE,
                CURRENCY_VND, new VndAmount(0));
    }

    public void debit(VndAmount amount) {
        Objects.requireNonNull(amount, "amount").requirePositive();
        if (status != CustomerAccount.Status.ACTIVE) {
            throw new ApplicationException(type == AccountType.TREASURY
                    ? AccountErrorCode.ACCOUNT_NOT_ACTIVE
                    : AccountErrorCode.SOURCE_ACCOUNT_FROZEN);
        }
        if (balance.value() < amount.value()) {
            throw new ApplicationException(type == AccountType.TREASURY
                    ? AccountErrorCode.INSUFFICIENT_TREASURY_FUNDS
                    : AccountErrorCode.INSUFFICIENT_FUNDS);
        }
        this.balance = this.balance.minus(amount);
    }

    public void credit(VndAmount amount) {
        Objects.requireNonNull(amount, "amount").requirePositive();
        if (status != CustomerAccount.Status.ACTIVE) {
            throw new ApplicationException(type == AccountType.TREASURY
                    ? AccountErrorCode.ACCOUNT_NOT_ACTIVE
                    : AccountErrorCode.RECIPIENT_ACCOUNT_FROZEN);
        }
        this.balance = this.balance.plus(amount);
    }

    public void changeStatus(CustomerAccount.Status newStatus) {
        Objects.requireNonNull(newStatus, "newStatus");
        if (type == AccountType.TREASURY) {
            throw new ApplicationException(AccountErrorCode.TREASURY_STATUS_IMMUTABLE);
        }
        this.status = newStatus;
    }

    public void initializeSupply(VndAmount initialSupply) {
        Objects.requireNonNull(initialSupply, "initialSupply").requirePositive();
        if (type != AccountType.TREASURY) {
            throw new ApplicationException(AccountErrorCode.INVALID_ACCOUNT_TYPE);
        }
        if (balance.value() != 0) {
            throw new ApplicationException(AccountErrorCode.TREASURY_ALREADY_INITIALIZED);
        }
        this.balance = initialSupply;
    }

    public CustomerAccount toCustomerAccount() {
        if (type != AccountType.CUSTOMER) {
            throw new IllegalStateException("Treasury account cannot be exported as CustomerAccount");
        }
        return new CustomerAccount(id, customerId, number, status, balance);
    }

    public UUID id() {
        return id;
    }

    public AccountNumber number() {
        return number;
    }

    public UUID customerId() {
        return customerId;
    }

    public AccountType type() {
        return type;
    }

    public CustomerAccount.Status status() {
        return status;
    }

    public String currency() {
        return currency;
    }

    public VndAmount balance() {
        return balance;
    }
}
