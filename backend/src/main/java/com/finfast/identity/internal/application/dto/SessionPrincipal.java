package com.finfast.identity.internal.application.dto;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Minimal server-held principal: no email, credentials, hash, cookie or CSRF token. */
public record SessionPrincipal(UUID userId, String role, UUID customerId, String displayName) implements Serializable {
    public SessionPrincipal {
        Objects.requireNonNull(userId);
        if (!"CUSTOMER".equals(role) && !"ADMIN".equals(role)) { throw new IllegalArgumentException("Unsupported role"); }
        if ("CUSTOMER".equals(role)) { Objects.requireNonNull(customerId); }
    }
    @Override public String toString() { return "SessionPrincipal[REDACTED]"; }
}
