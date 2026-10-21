package com.finfast.identity.api;

import java.util.Objects;
import java.util.UUID;

/** Trusted current-context capability; provider owner: Hưng.
 * Transfers/History require CUSTOMER; Administration requires ADMIN. Resolve
 * from the live server-managed identity/session, never caller-selected UUIDs.
 * Deny absent/expired/wrong-role identity before resource access/replay.
 * Frozen banking status must not revoke permitted reads. Actor records are
 * snapshots, not bearer credentials or proof obtained merely by constructing one.
 * Authentication adapters/session storage are not implemented by this contract.
 */
public interface CurrentIdentity {
    CustomerActor requireCustomer();

    AdminActor requireAdmin();

    record CustomerActor(UUID userId, UUID customerId) {
        public CustomerActor {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(customerId, "customerId");
        }
    }

    record AdminActor(UUID userId) {
        public AdminActor {
            Objects.requireNonNull(userId, "userId");
        }
    }
}
