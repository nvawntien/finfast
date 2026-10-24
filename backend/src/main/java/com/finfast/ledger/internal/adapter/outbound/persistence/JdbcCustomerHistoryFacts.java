package com.finfast.ledger.internal.adapter.outbound.persistence;

import com.finfast.ledger.internal.application.port.outbound.CustomerHistoryFacts;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

/** Bounded Ledger-only reads. Explicitly compose behind a Spring proxy on the History reader's resource. */
@Transactional(propagation = Propagation.MANDATORY, readOnly = true,
        isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
public class JdbcCustomerHistoryFacts implements CustomerHistoryFacts {
    private final JdbcTemplate jdbc;

    public JdbcCustomerHistoryFacts(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public long highWater() {
        LedgerJdbcTransaction.requireCommittedSnapshot(jdbc);
        return jdbc.queryForObject("""
                SELECT (SELECT last_commit_seq FROM public.dataset_control WHERE singleton_id = 1) AS position,
                       COALESCE((SELECT max(commit_seq) FROM public.financial_transaction), 0) AS maximum
                """, (row, index) -> {
            Long position = row.getObject("position", Long.class);
            long maximum = row.getLong("maximum");
            if (position == null && maximum == 0) {
                return 0L;
            }
            if (position == null || position < 1 || position != maximum) {
                throw new DataIntegrityViolationException("Inconsistent committed Ledger posting bound");
            }
            return position;
        });
    }

    @Override
    public List<FinancialTransaction> page(UUID participantAccountId, long highWater,
                                            OptionalLong beforeExclusive, int transactionLimit) {
        Objects.requireNonNull(participantAccountId, "participantAccountId");
        Objects.requireNonNull(beforeExclusive, "beforeExclusive");
        if (highWater < 0 || transactionLimit < 2 || transactionLimit > 101
                || (beforeExclusive.isPresent() && (beforeExclusive.getAsLong() < 2 || beforeExclusive.getAsLong() > highWater))) {
            throw new IllegalArgumentException("Invalid history query bounds");
        }
        LedgerJdbcTransaction.requireCommittedSnapshot(jdbc);
        Long before = beforeExclusive.isPresent() ? beforeExclusive.getAsLong() : null;
        // LIMIT applies to transaction headers, never entry rows. The outer join reconstructs each complete fact set.
        return jdbc.query("""
                WITH selected AS (
                    SELECT f.id, f.reference, f.type, f.amount_vnd, f.currency, f.commit_seq, f.committed_at
                    FROM public.financial_transaction f
                    WHERE f.type <> 'GENESIS' AND f.commit_seq <= ?
                      AND (?::bigint IS NULL OR f.commit_seq < ?::bigint)
                      AND EXISTS (SELECT 1 FROM public.ledger_entry p
                                  WHERE p.transaction_id = f.id AND p.account_id = ?)
                    ORDER BY f.commit_seq DESC
                    LIMIT ?
                )
                SELECT f.id, f.reference, f.type, f.amount_vnd, f.currency, f.commit_seq, f.committed_at,
                       isfinite(f.committed_at) AS finite_time,
                       e.id AS entry_id, e.account_id, e.delta_vnd
                FROM selected f LEFT JOIN public.ledger_entry e ON e.transaction_id = f.id
                ORDER BY f.commit_seq DESC, e.delta_vnd, e.id
                """, FinancialTransactionRows::readAll, highWater, before, before, participantAccountId, transactionLimit);
    }
}
