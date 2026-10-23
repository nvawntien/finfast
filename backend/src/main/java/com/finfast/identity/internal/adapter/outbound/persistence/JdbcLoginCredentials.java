package com.finfast.identity.internal.adapter.outbound.persistence;

import com.finfast.identity.internal.application.port.outbound.LoginCredentials;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

public final class JdbcLoginCredentials implements LoginCredentials {
    private final JdbcTemplate jdbc;
    public JdbcLoginCredentials(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Credential> find(String normalizedEmail) {
        return jdbc.query("SELECT id, role, password_hash FROM app_user WHERE email_normalized = ?",
                (row, index) -> new Credential(row.getObject("id", UUID.class), row.getString("role"), row.getString("password_hash")),
                normalizedEmail).stream().findFirst();
    }
    @Override public boolean upgradeHash(UUID userId, String expectedHash, String replacementHash) {
        return jdbc.update("UPDATE app_user SET password_hash = ? WHERE id = ? AND password_hash = ?",
                replacementHash, userId, expectedHash) == 1;
    }
}
