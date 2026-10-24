package com.finfast.administration.internal.application.port.outbound;

import com.finfast.administration.internal.domain.entity.AdminAuditEvent;

/**
 * Administration-owned insert-only port for a complete required allocation success audit.
 * Implementations must join the caller-owned Administration transaction, propagate persistence failures
 * and never commit independently. Return is not evidence of a durable commit.
 */
public interface AdminAuditStore {
    void insert(AdminAuditEvent event);
}
