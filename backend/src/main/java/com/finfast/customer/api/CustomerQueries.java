package com.finfast.customer.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Identity may resolve its authenticated User's profile; Administration may read
 * minimal authorized metadata. Provider owner: Hưng. No credentials or Accounts
 * repository/entity is exposed. Callers establish current authorization first.
 */
public interface CustomerQueries {
    Optional<Profile> findByUserId(UUID userId);

    Optional<Profile> findByCustomerId(UUID customerId);

    record Profile(UUID customerId, UUID userId, String displayName) {
        public Profile {
            Objects.requireNonNull(customerId, "customerId");
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(displayName, "displayName");
        }
    }
}
