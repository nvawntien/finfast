package com.finfast.identity.internal.adapter.outbound.persistence;

import com.finfast.common.error.ApplicationException;
import com.finfast.identity.internal.application.port.outbound.RegistrationUsers;
import com.finfast.identity.internal.application.usecase.RegistrationError;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.util.Objects;
import java.util.UUID;

/** Writes only Identity's table; concurrent normalized duplicates are arbitrated by PostgreSQL. */
public final class JdbcRegistrationUsers implements RegistrationUsers {
    private final JdbcTemplate jdbc;

    public JdbcRegistrationUsers(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

    @Override
    public void insertCustomer(UUID userId, String normalizedEmail, String passwordHash) {
        requireTransaction();
        int inserted = jdbc.update("""
                INSERT INTO app_user (id, email_normalized, password_hash, role)
                VALUES (?, ?, ?, 'CUSTOMER') ON CONFLICT (email_normalized) DO NOTHING
                """, userId, normalizedEmail, passwordHash);
        if (inserted != 1) { throw new ApplicationException(RegistrationError.EMAIL_ALREADY_REGISTERED); }
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException("Identity registration needs an existing write transaction");
        }
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            if (!DataSourceUtils.isConnectionTransactional(DataSourceUtils.getTargetConnection(connection),
                    Objects.requireNonNull(jdbc.getDataSource())) || connection.getAutoCommit()
                    || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                throw new IllegalTransactionStateException("Identity must join the registration JDBC transaction");
            }
            return null;
        });
    }
}
