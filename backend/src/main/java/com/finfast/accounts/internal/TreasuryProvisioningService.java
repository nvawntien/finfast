package com.finfast.accounts.internal;

import com.finfast.accounts.api.TreasuryProvisioning;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnBean(JdbcTemplate.class)
public class TreasuryProvisioningService implements TreasuryProvisioning {

    private final JdbcTemplate jdbc;

    public TreasuryProvisioningService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID openTreasury() {
        UUID existingId = jdbc.query(
                "SELECT id FROM account WHERE account_type = 'TREASURY'",
                rs -> rs.next() ? rs.getObject("id", UUID.class) : null);

        if (existingId != null) {
            return existingId;
        }

        UUID newId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO account (id, account_number, customer_id, account_type, status, currency, balance_vnd) "
                        + "VALUES (?, 'TREASURY', NULL, 'TREASURY', 'ACTIVE', 'VND', 0)",
                newId);
        return newId;
    }
}

