package com.finfast.accounts.api;

import com.finfast.common.money.VndAmount;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Treasury account state representation.
 * Ensures currency uses exact VndAmount (signed 64-bit integer BIGINT), never floating-point.
 */
public record TreasuryAccount(UUID accountId, AccountNumber number, VndAmount balance) {
    public TreasuryAccount {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(balance, "balance");
    }
}

