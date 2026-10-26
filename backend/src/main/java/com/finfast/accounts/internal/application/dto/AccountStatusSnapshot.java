package com.finfast.accounts.internal.application.dto;

import com.finfast.accounts.api.CustomerAccount;

import java.util.Objects;
import java.util.UUID;

/** Immutable state returned after the Accounts status row is locked. */
public record AccountStatusSnapshot(UUID accountId, boolean treasury, CustomerAccount.Status status) {
    public AccountStatusSnapshot {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(status, "status");
    }
}
