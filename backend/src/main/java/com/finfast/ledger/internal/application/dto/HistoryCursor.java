package com.finfast.ledger.internal.application.dto;

import java.util.Objects;
import java.util.UUID;

/** Authenticated traversal state; never authorization. GENESIS position 1 cannot be a Customer continuation. */
public record HistoryCursor(UUID ownerCustomerId, UUID participantAccountId, long highWater, long lastSeen, int limit) {
    public HistoryCursor {
        Objects.requireNonNull(ownerCustomerId, "ownerCustomerId");
        Objects.requireNonNull(participantAccountId, "participantAccountId");
        if (highWater < 2 || lastSeen < 2 || lastSeen > highWater || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid history cursor");
        }
    }
}
