package com.finfast.accounts.api;

import com.finfast.common.error.ErrorCode;
import com.finfast.common.money.VndAmount;

import java.util.Objects;
import java.util.UUID;

/** Ledger-only guarded mutation capability; provider owner: Hải.
 * Requires an existing outer transaction. Protect the full participant set,
 * revalidate current type/ownership/VND/ACTIVE/funds/numeric bounds, then apply
 * both sides together. No arbitrary deltas, setters, independent commits or
 * returned JDBC/lock handles. The coordination algorithm is NOT selected here.
 * Reject before any balance write; a technical failure escapes for full rollback.
 */
public interface PostingAccounts {
    Decision transfer(UUID sourceAccountId, UUID expectedSourceCustomerId,
                      AccountNumber recipientNumber, VndAmount amount);

    /** Select the singleton Treasury internally; callers cannot choose a debit account. */
    Decision allocate(AccountNumber recipientNumber, VndAmount amount);

    /** Only Ledger's controlled GENESIS implementation may call this once.
     * Require zero uninitialized Treasury and one atomic GENESIS/dataset setup;
     * never overwrite an existing balance or replenish an initialized dataset.
     */
    void initializeTreasury(UUID treasuryAccountId, VndAmount initialSupply);

    sealed interface Decision permits Applied, Rejected { }

    /** Pending paired changes in the current transaction, NOT commit evidence. */
    record Applied(UUID debitAccountId, UUID creditAccountId) implements Decision {
        public Applied {
            Objects.requireNonNull(debitAccountId, "debitAccountId");
            Objects.requireNonNull(creditAccountId, "creditAccountId");
            if (debitAccountId.equals(creditAccountId)) {
                throw new IllegalArgumentException("A paired movement requires distinct Accounts");
            }
        }
    }

    /** Deterministic pre-write rejection; caller must durably record its final outcome. */
    record Rejected(ErrorCode reason) implements Decision {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
