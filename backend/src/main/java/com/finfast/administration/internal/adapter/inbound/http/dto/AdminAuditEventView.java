package com.finfast.administration.internal.adapter.inbound.http.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AdminAuditEventView(
        Instant timestamp,
        UUID administratorId,
        String action,
        String target,
        String reason,
        String outcome,
        String reference,
        String beforeStatus,
        String afterStatus
) {
    public AdminAuditEventView {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(administratorId, "administratorId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(outcome, "outcome");
    }
}

