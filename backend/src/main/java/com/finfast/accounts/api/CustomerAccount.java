package com.finfast.accounts.api;

import com.finfast.common.money.VndAmount;

import java.util.Objects;
import java.util.UUID;

/** CUSTOMER-only VND snapshot, never a lock or authority to spend.
 * Accounts owns these values; recheck current status/funds inside posting.
 * Frozen owners retain authorized read access. No Treasury balance is exported.
 */
public record CustomerAccount(UUID accountId, UUID customerId, AccountNumber number,
                              Status status, VndAmount balance) {
    public CustomerAccount {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(balance, "balance");
    }

    public enum Status { ACTIVE, FROZEN }
}
