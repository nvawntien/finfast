package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.administration.internal.application.dto.AdminFinancialTransactionView;

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
        entries = List.copyOf(entries);
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
}
