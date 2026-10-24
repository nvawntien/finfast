package com.finfast.administration.internal.domain.entity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable facts for a successful demo allocation; no Account status transition is represented.
 * The trusted use case supplies the server-owned audit ID, authenticated administrator and timestamp.
 * Construction proves local shape only, not authorization, transaction existence, persistence or commit.
 */
public record AdminAuditEvent(UUID id, UUID adminUserId, UUID targetAccountId, String reason,
                              UUID transactionId, Instant createdAt) {

    public AdminAuditEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(adminUserId, "adminUserId");
        Objects.requireNonNull(targetAccountId, "targetAccountId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(createdAt, "createdAt");
        // PostgreSQL VARCHAR counts Unicode code points; encoding must not replace malformed UTF-16.
        if (reason.isBlank() || reason.indexOf('\0') >= 0
                || reason.codePointCount(0, reason.length()) > 500
                || !StandardCharsets.UTF_8.newEncoder().canEncode(reason)) {
            throw new IllegalArgumentException("Allocation audit reason must be nonblank, NUL-free, valid Unicode and fit 500 characters");
        }
    }

    public Action action() {
        return Action.DEMO_ALLOCATION;
    }

    public Outcome outcome() {
        return Outcome.SUCCESS;
    }

    public enum Action { DEMO_ALLOCATION }

    public enum Outcome { SUCCESS }
}
