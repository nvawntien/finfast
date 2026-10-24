package com.finfast.customer.internal.adapter.outbound.persistence;

import com.finfast.customer.internal.application.port.outbound.CustomerProfiles;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.util.Objects;
import java.util.UUID;

/** Customer owns only its profile; it never queries Identity or Accounts tables. */
public final class JdbcCustomerProfiles implements CustomerProfiles {
    private final JdbcTemplate jdbc;

    public JdbcCustomerProfiles(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

    @Override
    public void insert(UUID customerId, UUID userId, String displayName) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException("Customer profile needs an existing write transaction");
        }
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            if (!DataSourceUtils.isConnectionTransactional(DataSourceUtils.getTargetConnection(connection),
                    Objects.requireNonNull(jdbc.getDataSource())) || connection.getAutoCommit()
                    || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                throw new IllegalTransactionStateException("Customer must join the registration JDBC transaction");
            }
            return null;
        });
        jdbc.update("INSERT INTO customer (id, user_id, display_name) VALUES (?, ?, ?)", customerId, userId, displayName);
    }
}
