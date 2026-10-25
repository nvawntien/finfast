package com.finfast.accounts.internal.adapter.outbound.persistence;

import com.finfast.accounts.api.AccountIdentityQueries;
import com.finfast.accounts.api.AccountNumber;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Accounts-owned immutable identity projection for approved Ledger consumers. */
@Transactional(propagation = Propagation.MANDATORY, readOnly = true,
        isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
public class JdbcAccountIdentityQueries implements AccountIdentityQueries {
    private final JdbcTemplate jdbc;

    public JdbcAccountIdentityQueries(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<Identity> findById(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        requireReadSnapshot();
        return jdbc.query("""
                SELECT id, account_number, account_type, customer_id
                FROM public.account
                WHERE id = ?
                """, (row, index) -> new Identity(
                row.getObject("id", UUID.class),
                new AccountNumber(row.getString("account_number")),
                Kind.valueOf(row.getString("account_type")),
                Optional.ofNullable(row.getObject("customer_id", UUID.class))), accountId)
                .stream().findFirst();
    }

    private void requireReadSnapshot() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException("Account identity reads require a read-only REPEATABLE READ transaction");
        }
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            if (!DataSourceUtils.isConnectionTransactional(DataSourceUtils.getTargetConnection(connection),
                    Objects.requireNonNull(jdbc.getDataSource())) || connection.getAutoCommit()
                    || !connection.isReadOnly()
                    || connection.getTransactionIsolation() != Connection.TRANSACTION_REPEATABLE_READ) {
                throw new IllegalTransactionStateException(
                        "Account identity reads require bound read-only REPEATABLE READ JDBC");
            }
            return null;
        });
    }
}
