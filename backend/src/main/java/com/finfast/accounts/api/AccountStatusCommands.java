package com.finfast.accounts.api;

import java.util.Objects;
import java.util.UUID;

/** Administration-only Accounts capability; provider owner: Hải.
 * Requires an existing transaction. Administration first verifies ADMIN and a
 * nonempty reason, then commits this change and its required audit together.
 * Serialize with posting using the same Account protection; no Treasury target.
 * A same-status result is UNCHANGED. Technical/audit failures roll back.
 */
public interface AccountStatusCommands {
    Change changeCustomerStatus(UUID accountId, CustomerAccount.Status desiredStatus);

    record Change(UUID accountId, CustomerAccount.Status before, CustomerAccount.Status after) {
        public Change {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
        }

        public boolean changed() {
            return before != after;
        }
    }
}
