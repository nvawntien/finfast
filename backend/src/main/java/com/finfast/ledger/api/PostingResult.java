package com.finfast.ledger.api;

import com.finfast.common.error.ErrorCode;
import com.finfast.common.money.VndAmount;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Approved V1 Ledger posting result. Neither variant confirms a commit.
 * An initiating use case persists its final outcome (and allocation audit) in
 * the same transaction; only successful outer proxy return permits HTTP finality.
 */
public sealed interface PostingResult permits PostingResult.Pending, PostingResult.Rejected {
    record Pending(UUID transactionId, String reference, VndAmount amount,
                   Instant recordedAt) implements PostingResult {
        public Pending {
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(amount, "amount").requirePositive();
            Objects.requireNonNull(recordedAt, "recordedAt");
            if (reference.isBlank()) {
                throw new IllegalArgumentException("Posting reference must not be blank");
            }
        }
    }

    /** No balances/facts written; technical exceptions must never be converted to this. */
    record Rejected(ErrorCode reason) implements PostingResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
