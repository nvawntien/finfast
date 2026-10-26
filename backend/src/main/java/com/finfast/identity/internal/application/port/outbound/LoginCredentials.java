package com.finfast.identity.internal.application.port.outbound;

import com.finfast.identity.internal.application.dto.Credential;

import java.util.Optional;
import java.util.UUID;

/** Identity-owned credential access. Hashes never cross a public module API or session boundary. */
public interface LoginCredentials {
    Optional<Credential> find(String normalizedEmail);
    boolean upgradeHash(UUID userId, String expectedHash, String replacementHash);
}
