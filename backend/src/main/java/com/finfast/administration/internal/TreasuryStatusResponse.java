package com.finfast.administration.internal;

import java.time.Instant;
import java.util.UUID;

public record TreasuryStatusResponse(
        UUID accountId,
        String accountNumber,
        String status,
        String currency,
        String balanceVnd,
        String initialSupplyVnd,
        UUID genesisTransactionId,
        Instant genesisCommittedAt
) {}

