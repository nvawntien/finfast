package com.finfast.ledger.api;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.common.money.VndAmount;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Transfers-only ordinary posting capability; provider owner: Tiến.
 * Requires the existing Transfers transaction and a server-authorized source.
 * Use PostingAccounts, write exactly two opposite entries and one immutable
 * transaction/reference. No GENESIS/allocation operation is exposed.
 * New-intent eligibility follows scoped replay/conflict resolution.
 */
public interface FinancialPosting {
    PostingResult postTransfer(Intent intent);

    /** Trusted UUIDs are established server-side, never copied from client identity/source fields.
     * Zero is representable so posting can reject it before writes. Malformed,
     * negative or overflowing raw inputs stay in Idempotency's canonical intent
     * and are rejected by the initiating use case before mapping to VndAmount.
     * Preserve absent versus empty note; no implicit normalization.
     */
    record Intent(UUID initiatorUserId, UUID sourceCustomerId, UUID sourceAccountId,
                  AccountNumber recipientNumber, VndAmount amount, Optional<String> note) {
        public Intent {
            Objects.requireNonNull(initiatorUserId, "initiatorUserId");
            Objects.requireNonNull(sourceCustomerId, "sourceCustomerId");
            Objects.requireNonNull(sourceAccountId, "sourceAccountId");
            Objects.requireNonNull(recipientNumber, "recipientNumber");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(note, "note");
        }
    }
}
