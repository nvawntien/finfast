package com.finfast.identity.internal.application.port.outbound;

import java.util.UUID;

/** Identity-owned persistence; must join the existing registration transaction. */
public interface RegistrationUsers {
    void insertCustomer(UUID userId, String normalizedEmail, String passwordHash);
}
