package com.finfast.administration.internal.application.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AdminAuditRecordView(
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
    public AdminAuditRecordView {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(administratorId, "administratorId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(outcome, "outcome");
    }
}

