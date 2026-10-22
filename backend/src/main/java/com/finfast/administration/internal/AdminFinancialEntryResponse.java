package com.finfast.administration.internal;

import java.util.UUID;

public record AdminFinancialEntryResponse(
        UUID accountId,
        String accountNumber,
        String deltaVnd
) {}

