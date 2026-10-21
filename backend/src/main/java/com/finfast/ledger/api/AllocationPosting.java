package com.finfast.ledger.api;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.common.money.VndAmount;

import java.util.Objects;
import java.util.UUID;

/** Administration-only ordinary allocation posting; provider owner: Tiến.
 * Caller authorizes ADMIN, resolves the full idempotency scope and validates
 * a nonempty reason before posting. Requires the existing allocation transaction.
 * Ledger selects Treasury via PostingAccounts, validates both Accounts and writes
 * two opposite entries. Administration joins the required audit and final outcome.
 * No independent commit, source selection or supply creation is permitted.
 */
public interface AllocationPosting {
    PostingResult postAllocation(Intent intent);

    /** Reason is preserved exactly and remains an eligibility check before writes. */
    record Intent(UUID adminUserId, AccountNumber recipientNumber, VndAmount amount, String reason) {
        public Intent {
            Objects.requireNonNull(adminUserId, "adminUserId");
            Objects.requireNonNull(recipientNumber, "recipientNumber");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
