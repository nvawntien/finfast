package com.finfast.accounts.internal.adapter.outbound.persistence;

import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.internal.application.port.outbound.AccountStatusRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL status persistence; the caller owns the outer transaction and row lock. */
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public class JdbcAccountStatusRepository implements AccountStatusRepository {
    private final JdbcTemplate jdbc;

    public JdbcAccountStatusRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<LockedAccount> lockById(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        AccountsJdbcTransaction.requireWrite(jdbc);
        return jdbc.query("""
                SELECT id, account_type, status
                FROM public.account
                WHERE id = ?
                FOR UPDATE
                """, (row, index) -> new LockedAccount(
                row.getObject("id", UUID.class),
                "TREASURY".equals(row.getString("account_type")),
                CustomerAccount.Status.valueOf(row.getString("status"))), accountId).stream().findFirst();
    }

    @Override
    public void updateStatus(UUID accountId, CustomerAccount.Status status) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(status, "status");
        AccountsJdbcTransaction.requireWrite(jdbc);
        int affected = jdbc.update("UPDATE public.account SET status = ? WHERE id = ?", status.name(), accountId);
        if (affected != 1) {
            throw new DataIntegrityViolationException("Expected exactly one Account status row to update");
        }
    }
}
