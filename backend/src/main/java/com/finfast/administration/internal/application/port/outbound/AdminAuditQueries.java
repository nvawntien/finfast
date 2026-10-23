package com.finfast.administration.internal.application.port.outbound;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AdminAuditQueries {

    List<AdminAuditEventView> listAll();

    record AdminAuditEventView(
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
    ) {}
}

