package com.finfast.accounts.internal.application.port.outbound;

import com.finfast.accounts.api.CustomerAccount;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Accounts-local persistence boundary for the locked status mutation. */
public interface AccountStatusRepository {
    Optional<LockedAccount> lockById(UUID accountId);

    void updateStatus(UUID accountId, CustomerAccount.Status status);

    record LockedAccount(UUID accountId, boolean treasury, CustomerAccount.Status status) {
        public LockedAccount {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(status, "status");
        }
    }
}
