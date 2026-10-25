package com.finfast.administration.internal.adapter.inbound.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.finfast.administration.internal.application.dto.AdminAuditRecordView;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminAuditEventResponse(
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
    public AdminAuditEventResponse {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(administratorId, "administratorId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(outcome, "outcome");
    }

    public static AdminAuditEventResponse from(AdminAuditRecordView view) {
        Objects.requireNonNull(view, "view");
        return new AdminAuditEventResponse(
                view.timestamp(),
                view.administratorId(),
                view.action(),
                view.target(),
                view.reason(),
                view.outcome(),
                view.reference(),
                view.beforeStatus(),
                view.afterStatus()
        );
    }
}
