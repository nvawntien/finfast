package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.administration.internal.application.dto.AdminFinancialTransactionView;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AdminTransactionResponse(
        String reference,
        String type,
        String amountVnd,
        String currency,
        Instant committedAt,
        List<EntryResponse> entries
) {
    public AdminTransactionResponse {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amountVnd, "amountVnd");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(committedAt, "committedAt");
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public record EntryResponse(String accountNumber, String deltaVnd) {
        public EntryResponse {
            Objects.requireNonNull(accountNumber, "accountNumber");
            Objects.requireNonNull(deltaVnd, "deltaVnd");
        }
    }

    public static AdminTransactionResponse from(AdminFinancialTransactionView view) {
        Objects.requireNonNull(view, "view");
        List<EntryResponse> mappedEntries = view.entries().stream()
                .map(e -> new EntryResponse(e.accountNumber(), e.deltaVnd()))
                .toList();
        return new AdminTransactionResponse(
                view.reference(),
                view.type(),
                view.amountVnd(),
                view.currency(),
                view.committedAt(),
                mappedEntries
        );
    }

    public static AdminTransactionResponse from(AdminFinancialTransactionQueries.Transaction tx) {
        Objects.requireNonNull(tx, "tx");
        List<EntryResponse> mappedEntries = tx.entries().stream()
                .map(e -> new EntryResponse(e.accountNumber().value(), String.valueOf(e.deltaVnd())))
                .toList();
        return new AdminTransactionResponse(
                tx.reference(),
                tx.type().name(),
                String.valueOf(tx.amount().value()),
                "VND",
                tx.recordedAt(),
                mappedEntries
        );
    }
}
