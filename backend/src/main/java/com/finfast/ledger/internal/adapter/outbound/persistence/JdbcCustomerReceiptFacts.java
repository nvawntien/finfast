package com.finfast.ledger.internal.adapter.outbound.persistence;

import com.finfast.ledger.internal.application.port.outbound.CustomerReceiptFacts;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded Ledger receipt lookup; callers must supply the read-only REPEATABLE READ transaction. */
@Transactional(propagation = Propagation.MANDATORY, readOnly = true,
        isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
public class JdbcCustomerReceiptFacts implements CustomerReceiptFacts {
    private final JdbcTemplate jdbc;

    public JdbcCustomerReceiptFacts(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<FinancialTransaction> find(UUID participantAccountId, String reference) {
        Objects.requireNonNull(participantAccountId, "participantAccountId");
        Objects.requireNonNull(reference, "reference");
        if (reference.isBlank() || reference.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        LedgerJdbcTransaction.requireCommittedSnapshot(jdbc);
        return jdbc.query("""
                SELECT f.id, f.reference, f.type, f.amount_vnd, f.currency, f.commit_seq, f.committed_at,
                       isfinite(f.committed_at) AS finite_time,
                       e.id AS entry_id, e.account_id, e.delta_vnd
                FROM public.financial_transaction f
                LEFT JOIN public.ledger_entry e ON e.transaction_id = f.id
                WHERE f.reference = ?
                  AND f.type <> 'GENESIS'
                  AND EXISTS (SELECT 1 FROM public.ledger_entry p
                              WHERE p.transaction_id = f.id AND p.account_id = ?)
                ORDER BY e.delta_vnd, e.id
                """, FinancialTransactionRows::readAll, reference, participantAccountId)
                .stream().findFirst();
    }
}
