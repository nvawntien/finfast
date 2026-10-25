package com.finfast.administration.internal.adapter.outbound.persistence;

import com.finfast.administration.internal.application.port.outbound.AdminAuditStore;
import com.finfast.administration.internal.domain.entity.AdminAuditEvent;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Insert-only Administration audit storage, to be composed behind a Spring transaction proxy.
 * Joins the caller-owned transaction; a successful return does not acknowledge its commit.
 * No automatic bean registration or allocation orchestration is installed here.
 */
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public class JdbcAdminAuditRepository implements AdminAuditStore {

    // pgJDBC encodes earlier OffsetDateTime values as -infinity, losing the supplied instant.
    private static final OffsetDateTime MIN_JDBC_TIMESTAMP =
            OffsetDateTime.of(-4712, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final JdbcTemplate jdbc;

    public JdbcAdminAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void insert(AdminAuditEvent event) {
        Objects.requireNonNull(event, "event");
        AdminAuditJdbcTransaction.require(jdbc);
        // Bind a UTC JDBC-supported type at PostgreSQL precision, without mutating the event.
        OffsetDateTime createdAt = event.createdAt().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        if (createdAt.isBefore(MIN_JDBC_TIMESTAMP)) {
            throw new DateTimeException("Audit timestamp cannot be represented as a finite JDBC timestamp");
        }
        int inserted = jdbc.update("""
                INSERT INTO public.admin_audit_event
                    (id, admin_user_id, target_account_id, action, reason, outcome,
                     transaction_id, before_status, after_status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, event.id(), event.adminUserId(), event.targetAccountId(), event.action().name(),
                event.reason(), event.outcome().name(), event.transactionId(), event.beforeStatus(),
                event.afterStatus(), createdAt);
        if (inserted != 1) {
            throw new DataIntegrityViolationException("Expected exactly one inserted Admin audit row");
        }
    }
}
