package com.finfast.accounts.internal.adapter.outbound.persistence;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.util.Objects;

/** Verifies that Accounts mutations use the caller-owned bound JDBC transaction. */
final class AccountsJdbcTransaction {
    private AccountsJdbcTransaction() {
    }

    static void requireWrite(JdbcTemplate jdbc) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException("Accounts status mutation requires a write transaction");
        }
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            if (!DataSourceUtils.isConnectionTransactional(DataSourceUtils.getTargetConnection(connection),
                    Objects.requireNonNull(jdbc.getDataSource()))
                    || connection.getAutoCommit() || connection.isReadOnly()
                    || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                throw new IllegalTransactionStateException(
                        "Accounts status mutation requires bound READ COMMITTED JDBC");
            }
            return null;
        });
    }
}
