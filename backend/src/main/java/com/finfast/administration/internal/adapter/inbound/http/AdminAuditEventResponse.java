package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.administration.internal.application.port.outbound.AdminAuditQueries;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AdminAuditEventResponse(
        UUID id,
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
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(outcome, "outcome");
    }

    public static AdminAuditEventResponse from(AdminAuditQueries.AdminAuditEventView view) {
        Objects.requireNonNull(view, "view");
        return new AdminAuditEventResponse(
                view.id(),
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

    public static AdminAuditEventResponse from(AdminAuditEventResponse response) {
        return Objects.requireNonNull(response, "response");
    }
}