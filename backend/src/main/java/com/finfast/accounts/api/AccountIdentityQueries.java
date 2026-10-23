package com.finfast.accounts.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * TD-016-AM-02 APPROVED — V1 (2026-10-23). Accounts-owned; Ledger only.
 * Resolve immutable identity for participant-bound history/cursors and permitted financial labels.
 * Non-null ID; empty means missing Account, never a SQL failure. Inconsistent identity fails technically.
 * Return a consistent snapshot; no locks, balance, status, credentials or Customer display information.
 * Ledger must not disclose Treasury numbers through Customer projections; identity is no authorization token.
 */
public interface AccountIdentityQueries {
    Optional<Identity> findById(UUID accountId);

    enum Kind { CUSTOMER, TREASURY }

    record Identity(UUID accountId, AccountNumber number, Kind kind, Optional<UUID> customerId) {
        public Identity {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(number, "number");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(customerId, "customerId");
            if ((kind == Kind.CUSTOMER) != customerId.isPresent()) {
                throw new IllegalArgumentException("Only Customer Accounts have a Customer owner");
            }
        }
    }
}
