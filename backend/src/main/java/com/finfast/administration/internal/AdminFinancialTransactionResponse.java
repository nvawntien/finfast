package com.finfast.administration.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminFinancialTransactionResponse(
        UUID id,
        String reference,
        String type,
        String amountVnd,
        String currency,
        long commitSeq,
        Instant committedAt,
        List<AdminFinancialEntryResponse> entries
) {}

