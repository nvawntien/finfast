package com.finfast.customer.api;

import java.util.Objects;
import java.util.UUID;

/** Identity-only registration collaborator; provider owner: Hưng.
 * Requires the existing registration transaction and a newly persisted CUSTOMER
 * User. Create its profile and call Accounts AccountProvisioning atomically.
 * Failure rolls back all three records. No credentials or caller-selected role.
 */
public interface CustomerProvisioning {
    Registration provision(UUID userId, String displayName);

    /** Registration projection avoids exposing Accounts types to Identity:
     * no new identity -> accounts edge. Account is guaranteed ACTIVE/VND/zero.
     */
    record Registration(UUID customerId, String accountNumber) {
        public Registration {
            Objects.requireNonNull(customerId, "customerId");
            Objects.requireNonNull(accountNumber, "accountNumber");
        }
    }
}
