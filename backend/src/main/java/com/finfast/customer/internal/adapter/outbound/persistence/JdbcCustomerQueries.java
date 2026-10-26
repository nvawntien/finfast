package com.finfast.customer.internal.adapter.outbound.persistence;

import com.finfast.customer.api.CustomerQueries;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

/** Customer-owned minimal profile projection; no Identity/Accounts table access. */
public final class JdbcCustomerQueries implements CustomerQueries {
    private final JdbcTemplate jdbc;
    public JdbcCustomerQueries(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Profile> findByUserId(UUID userId) { return find("user_id", userId); }
    @Override public Optional<Profile> findByCustomerId(UUID customerId) { return find("id", customerId); }
    private Optional<Profile> find(String column, UUID value) {
        // Column is one of the two private method-call constants, never caller input.
        return jdbc.query("SELECT id, user_id, display_name FROM customer WHERE " + column + " = ?",
                (row, index) -> new Profile(row.getObject("id", UUID.class), row.getObject("user_id", UUID.class),
                        row.getString("display_name")), value).stream().findFirst();
    }
}
