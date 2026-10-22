package com.finfast.ledger.internal.adapter.outbound.persistence;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.util.Objects;

/** Checks the actual JDBC resource as well as the intercepted MANDATORY boundary. */
final class LedgerJdbcTransaction {

    private LedgerJdbcTransaction() {
    }

    static void require(JdbcTemplate jdbc, boolean writing) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || (writing && TransactionSynchronizationManager.isCurrentTransactionReadOnly())) {
            throw new IllegalTransactionStateException("Ledger persistence requires an existing compatible transaction");
        }
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            // JdbcTemplate supplies a close-suppressing proxy, not the raw resource bound by the transaction manager.
            if (!DataSourceUtils.isConnectionTransactional(DataSourceUtils.getTargetConnection(connection),
                    Objects.requireNonNull(jdbc.getDataSource()))
                    || connection.getAutoCommit()
                    || (writing && connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED)) {
                throw new IllegalTransactionStateException("Ledger JDBC must join the caller's READ COMMITTED transaction");
            }
            return null;
        });
    }
}
