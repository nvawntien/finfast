package com.finfast.administration.internal.domain.entity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable facts for a successful allocation or Customer Account status operation.
 * The trusted use case supplies the server-owned audit ID, authenticated administrator and timestamp.
 * Construction proves local shape only, not authorization, persistence or commit.
 */
public record AdminAuditEvent(UUID id, UUID adminUserId, UUID targetAccountId, String reason,
                              UUID transactionId, Instant createdAt, String actionName, String outcomeName,
                              String beforeStatus, String afterStatus) {

    public AdminAuditEvent(UUID id, UUID adminUserId, UUID targetAccountId, String reason,
                           UUID transactionId, Instant createdAt) {
        this(id, adminUserId, targetAccountId, reason, requireTransaction(transactionId), createdAt,
                Action.DEMO_ALLOCATION.name(), Outcome.SUCCESS.name(), null, null);
    }

    private static UUID requireTransaction(UUID transactionId) {
        return Objects.requireNonNull(transactionId, "transactionId");
    }

    public AdminAuditEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(adminUserId, "adminUserId");
        Objects.requireNonNull(targetAccountId, "targetAccountId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(actionName, "actionName");
        Objects.requireNonNull(outcomeName, "outcome");
        // PostgreSQL VARCHAR counts Unicode code points; encoding must not replace malformed UTF-16.
        if (reason.isBlank() || reason.indexOf('\0') >= 0
                || reason.codePointCount(0, reason.length()) > 500
                || !StandardCharsets.UTF_8.newEncoder().canEncode(reason)) {
            throw new IllegalArgumentException("Admin audit reason must be nonblank, NUL-free, valid Unicode and fit 500 characters");
        }
        validateShape(actionName, outcomeName, transactionId, beforeStatus, afterStatus);
    }

    public Action action() {
        return Action.valueOf(actionName);
    }

    public Outcome outcome() {
        return Outcome.valueOf(outcomeName);
    }

    public static AdminAuditEvent statusChange(UUID id, UUID adminUserId, UUID targetAccountId, String reason,
                                               Action action, Outcome outcome, String beforeStatus,
                                               String afterStatus, Instant createdAt) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(outcome, "outcome");
        return new AdminAuditEvent(id, adminUserId, targetAccountId, reason, null, createdAt,
                action.name(), outcome.name(), beforeStatus, afterStatus);
    }

    private static void validateShape(String actionName, String outcomeName, UUID transactionId,
                                      String beforeStatus, String afterStatus) {
        Action action = Action.valueOf(actionName);
        Outcome outcome = Outcome.valueOf(outcomeName);
        if (action == Action.DEMO_ALLOCATION) {
            if (outcome != Outcome.SUCCESS || transactionId == null || beforeStatus != null || afterStatus != null) {
                throw new IllegalArgumentException("Allocation audit shape is invalid");
            }
            return;
        }
        if (transactionId != null || beforeStatus == null || afterStatus == null
                || (!beforeStatus.equals("ACTIVE") && !beforeStatus.equals("FROZEN"))
                || (!afterStatus.equals("ACTIVE") && !afterStatus.equals("FROZEN"))) {
            throw new IllegalArgumentException("Account status audit shape is invalid");
        }
        if (outcome == Outcome.UNCHANGED) {
            if (!beforeStatus.equals(afterStatus)) {
                throw new IllegalArgumentException("Unchanged status audit must retain the same status");
            }
            return;
        }
        boolean freeze = action == Action.FREEZE_ACCOUNT
                && beforeStatus.equals("ACTIVE") && afterStatus.equals("FROZEN");
        boolean unfreeze = action == Action.UNFREEZE_ACCOUNT
                && beforeStatus.equals("FROZEN") && afterStatus.equals("ACTIVE");
        if (outcome != Outcome.SUCCESS || (!freeze && !unfreeze)) {
            throw new IllegalArgumentException("Account status transition audit shape is invalid");
        }
    }

    public enum Action { DEMO_ALLOCATION, FREEZE_ACCOUNT, UNFREEZE_ACCOUNT }

    public enum Outcome { SUCCESS, UNCHANGED }
}
