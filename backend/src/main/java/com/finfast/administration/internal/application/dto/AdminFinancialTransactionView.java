package com.finfast.administration.internal.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AdminFinancialTransactionView(
        String reference,
        String type,
        String amountVnd,
        String currency,
        Instant committedAt,
        List<EntryView> entries
) {
    public AdminFinancialTransactionView {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amountVnd, "amountVnd");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(committedAt, "committedAt");
        entries = List.copyOf(entries);
    }

    public record EntryView(String accountNumber, String deltaVnd) {
        public EntryView {
            Objects.requireNonNull(accountNumber, "accountNumber");
            Objects.requireNonNull(deltaVnd, "deltaVnd");
        }
    }
}

