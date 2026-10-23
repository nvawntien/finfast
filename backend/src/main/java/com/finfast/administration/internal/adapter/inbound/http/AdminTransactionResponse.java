package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.ledger.api.AdminFinancialTransactionQueries;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AdminTransactionResponse(
        UUID transactionId,
        String reference,
        String type,
        long amountVnd,
        String currency,
        Instant committedAt,
        List<EntryResponse> entries
) {
    public AdminTransactionResponse {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(committedAt, "committedAt");
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public record EntryResponse(String accountNumber, long deltaVnd) {
        public EntryResponse {
            Objects.requireNonNull(accountNumber, "accountNumber");
        }
    }

    public static AdminTransactionResponse from(AdminFinancialTransactionQueries.Transaction tx) {
        Objects.requireNonNull(tx, "tx");
        List<EntryResponse> mappedEntries = tx.entries().stream()
                .map(e -> new EntryResponse(e.accountNumber().value(), e.deltaVnd()))
                .toList();
        return new AdminTransactionResponse(
                tx.transactionId(),
                tx.reference(),
                tx.type().name(),
                tx.amount().value(),
                "VND",
                tx.recordedAt(),
                mappedEntries
        );
    }

    public static AdminTransactionResponse from(AdminTransactionResponse response) {
        return Objects.requireNonNull(response, "response");
    }
}

