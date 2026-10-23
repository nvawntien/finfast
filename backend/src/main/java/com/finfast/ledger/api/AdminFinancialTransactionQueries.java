package com.finfast.ledger.api;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.common.money.VndAmount;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * TD-016-AM-04 APPROVED — V1 (2026-10-23). Ledger-owned; Administration only.
 * Caller establishes ADMIN authorization. Read one consistent committed read-only snapshot;
 * reject incompatible write transactions, excluding pending/rejected requests and Admin audit rows.
 * Include GENESIS as initialization. Ledger resolves numbers through Accounts AccountIdentityQueries,
 * never Account SQL. Currency is always VND. No generic query/filter/pagination API or mutation.
 * Empty means no committed facts; SQL/corrupt-fact failures propagate. Returned UUIDs enable audit correlation.
 */
public interface AdminFinancialTransactionQueries {
    Transactions listCommitted();

    enum Type { INTERNAL_TRANSFER, DEMO_ALLOCATION, GENESIS }

    record Transactions(List<Transaction> items) {
        public Transactions {
            items = List.copyOf(items);
        }
    }

    record Entry(AccountNumber accountNumber, long deltaVnd) {
        public Entry {
            Objects.requireNonNull(accountNumber, "accountNumber");
            if (deltaVnd == 0) {
                throw new IllegalArgumentException("A financial entry must have a nonzero signed delta");
            }
        }
    }

    record Transaction(UUID transactionId, String reference, Type type, VndAmount amount,
                       Instant recordedAt, List<Entry> entries) {
        public Transaction {
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(reference, "reference");
            if (reference.isBlank() || reference.indexOf('\0') >= 0
                    || reference.codePointCount(0, reference.length()) > 64
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(reference)) {
                throw new IllegalArgumentException("Financial reference must be nonblank, NUL-free and fit 64 characters");
            }
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(amount, "amount").requirePositive();
            Objects.requireNonNull(recordedAt, "recordedAt");
            entries = List.copyOf(entries);
            if (type == Type.GENESIS) {
                if (entries.size() != 1 || entries.getFirst().deltaVnd() != amount.value()) {
                    throw new IllegalArgumentException("GENESIS requires one positive entry matching its supply");
                }
            } else if (entries.size() != 2 || entries.get(0).accountNumber().equals(entries.get(1).accountNumber())
                    || !((entries.get(0).deltaVnd() == -amount.value() && entries.get(1).deltaVnd() == amount.value())
                    || (entries.get(1).deltaVnd() == -amount.value() && entries.get(0).deltaVnd() == amount.value()))) {
                throw new IllegalArgumentException("Ordinary transactions require two distinct opposite entries matching amount");
            }
        }
    }
}
