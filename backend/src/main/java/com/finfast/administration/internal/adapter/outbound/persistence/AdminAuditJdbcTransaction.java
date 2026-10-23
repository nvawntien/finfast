package com.finfast.administration.internal.adapter.outbound.persistence;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.util.Objects;

/** Checks the actual caller-bound JDBC resource even when transaction interception is bypassed. */
final class AdminAuditJdbcTransaction {

    private AdminAuditJdbcTransaction() {
    }

    static void require(JdbcTemplate jdbc) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException("Admin audit persistence requires an existing write transaction");
        }
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            // JdbcTemplate wraps the transaction-bound connection in a close-suppressing proxy.
            if (!DataSourceUtils.isConnectionTransactional(DataSourceUtils.getTargetConnection(connection),
                    Objects.requireNonNull(jdbc.getDataSource()))
                    || connection.getAutoCommit() || connection.isReadOnly()
                    || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                throw new IllegalTransactionStateException("Admin audit JDBC must join the caller's READ COMMITTED write transaction");
            }
            return null;
        });
    }
}
