package com.finfast.ledger.internal.domain.entity;

import com.finfast.common.money.VndAmount;
import com.finfast.common.money.VndArithmetic;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, complete V1 financial facts. Construction proves local invariants,
 * not Account eligibility, persistence, a unique dataset GENESIS or a durable commit.
 * Identity, reference, posting position and time are supplied by the caller.
 */
public final class FinancialTransaction {

    private final UUID id;
    private final String reference;
    private final Type type;
    private final VndAmount amount;
    private final long commitSeq;
    private final Instant recordedAt;
    private final List<Entry> entries;

    private FinancialTransaction(UUID id, String reference, Type type, VndAmount amount,
                                 long commitSeq, Instant recordedAt, List<Entry> entries,
                                 UUID treasuryAccountId) {
        this.id = Objects.requireNonNull(id, "id");
        this.reference = Objects.requireNonNull(reference, "reference");
        this.type = Objects.requireNonNull(type, "type");
        this.amount = Objects.requireNonNull(amount, "amount").requirePositive();
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (reference.isBlank() || reference.indexOf('\0') >= 0
                || reference.codePointCount(0, reference.length()) > 64) {
            throw new IllegalArgumentException("Transaction reference must be nonblank, NUL-free and fit 64 characters");
        }
        if (commitSeq <= 0) {
            throw new IllegalArgumentException("Supplied posting position must be positive");
        }
        this.commitSeq = commitSeq;

        switch (type) {
            case INTERNAL_TRANSFER, DEMO_ALLOCATION -> validatePair();
            case GENESIS -> validateGenesis(Objects.requireNonNull(treasuryAccountId, "treasuryAccountId"));
        }
    }

    public static FinancialTransaction internalTransfer(UUID id, String reference, VndAmount amount,
                                                        long commitSeq, Instant recordedAt, List<Entry> entries) {
        return new FinancialTransaction(id, reference, Type.INTERNAL_TRANSFER, amount,
                commitSeq, recordedAt, entries, null);
    }

    public static FinancialTransaction demoAllocation(UUID id, String reference, VndAmount amount,
                                                      long commitSeq, Instant recordedAt, List<Entry> entries) {
        return new FinancialTransaction(id, reference, Type.DEMO_ALLOCATION, amount,
                commitSeq, recordedAt, entries, null);
    }

    /** The supplied Treasury UUID is a participant identity, not proof of its actual Account type. */
    public static FinancialTransaction genesis(UUID id, String reference, VndAmount amount,
                                               long commitSeq, Instant recordedAt, UUID treasuryAccountId,
                                               List<Entry> entries) {
        return new FinancialTransaction(id, reference, Type.GENESIS, amount,
                commitSeq, recordedAt, entries, treasuryAccountId);
    }

    private void validatePair() {
        if (entries.size() != 2) {
            throw new IllegalArgumentException("An ordinary transaction requires exactly two entries");
        }
        Entry first = entries.getFirst();
        Entry second = entries.getLast();
        if (first.id().equals(second.id())) {
            throw new IllegalArgumentException("Entry identities must be distinct within a transaction");
        }
        if (first.accountId().equals(second.accountId())) {
            throw new IllegalArgumentException("An ordinary transaction requires two distinct Accounts");
        }

        long credit = amount.value();
        long debit = VndArithmetic.negateExact(credit);
        // Exact inverse magnitudes also establish zero sum, without summing or taking abs of unchecked deltas.
        if (!((first.deltaVnd() == debit && second.deltaVnd() == credit)
                || (first.deltaVnd() == credit && second.deltaVnd() == debit))) {
            throw new IllegalArgumentException("Ordinary entries must be exactly minus and plus the transaction amount");
        }
    }

    private void validateGenesis(UUID treasuryAccountId) {
        if (entries.size() != 1) {
            throw new IllegalArgumentException("GENESIS requires exactly one entry");
        }
        Entry entry = entries.getFirst();
        if (!entry.accountId().equals(treasuryAccountId) || entry.deltaVnd() != amount.value()) {
            throw new IllegalArgumentException("GENESIS requires the positive supply entry on the supplied Treasury Account");
        }
    }

    public UUID id() {
        return id;
    }

    public String reference() {
        return reference;
    }

    public Type type() {
        return type;
    }

    public VndAmount amount() {
        return amount;
    }

    /** Supplied position only; this object neither allocates nor proves commit order. */
    public long commitSeq() {
        return commitSeq;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    public List<Entry> entries() {
        return entries;
    }

    public enum Type { INTERNAL_TRANSFER, DEMO_ALLOCATION, GENESIS }

    /** Signed Account balance delta, distinct from a nonnegative VndAmount or a GL debit/credit category. */
    public record Entry(UUID id, UUID accountId, long deltaVnd) {
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(accountId, "accountId");
            if (deltaVnd == 0) {
                throw new IllegalArgumentException("Ledger entry delta must be nonzero");
            }
        }
    }
}
