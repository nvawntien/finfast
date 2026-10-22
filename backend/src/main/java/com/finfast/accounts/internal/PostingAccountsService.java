package com.finfast.accounts.internal;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.PostingAccounts;
import com.finfast.common.money.VndAmount;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnBean(JdbcTemplate.class)
public class PostingAccountsService implements PostingAccounts {

    private final JdbcTemplate jdbc;

    public PostingAccountsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Decision transfer(UUID sourceAccountId, UUID expectedSourceCustomerId,
                              AccountNumber recipientNumber, VndAmount amount) {
        throw new UnsupportedOperationException("Transfer posting implementation deferred to transfers module");
    }

    @Override
    public Decision allocate(AccountNumber recipientNumber, VndAmount amount) {
        throw new UnsupportedOperationException("Allocation posting implementation deferred to administration module");
    }

    @Override
    public void initializeTreasury(UUID treasuryAccountId, VndAmount initialSupply) {
        Objects.requireNonNull(treasuryAccountId, "treasuryAccountId");
        Objects.requireNonNull(initialSupply, "initialSupply");
        initialSupply.requirePositive();

        int updated = jdbc.update(
                "UPDATE account SET balance_vnd = ? WHERE id = ? AND account_type = 'TREASURY' AND balance_vnd = 0",
                initialSupply.value(), treasuryAccountId);

        if (updated != 1) {
            Long currentBalance = jdbc.query(
                    "SELECT balance_vnd FROM account WHERE id = ? AND account_type = 'TREASURY'",
                    rs -> rs.next() ? rs.getLong("balance_vnd") : null,
                    treasuryAccountId);

            if (currentBalance == null) {
                throw new IllegalStateException("Treasury account does not exist: " + treasuryAccountId);
            }
            if (currentBalance > 0) {
                throw new IllegalStateException("Treasury account is already funded with balance: " + currentBalance);
            }
            throw new IllegalStateException("Failed to update Treasury balance for account: " + treasuryAccountId);
        }
    }
}

