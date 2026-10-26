package com.finfast.identity.internal.application.dto;

import java.util.UUID;

/** Internal credential projection; never expose through a public API, session or diagnostics. */
public record Credential(UUID userId, String role, String passwordHash) {
    @Override public String toString() { return "Credential[REDACTED]"; }
}
