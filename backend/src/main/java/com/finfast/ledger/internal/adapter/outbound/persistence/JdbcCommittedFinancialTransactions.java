package com.finfast.ledger.internal.adapter.outbound.persistence;

import com.finfast.ledger.internal.application.port.outbound.CommittedFinancialTransactions;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Ledger-only SQL; explicitly compose behind a transaction proxy with the AM-04 application entry. */
@Transactional(propagation = Propagation.MANDATORY, readOnly = true,
        isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
public class JdbcCommittedFinancialTransactions implements CommittedFinancialTransactions {
    private final JdbcTemplate jdbc;
    private final JdbcGenesisDatasetInspection dataset;

    public JdbcCommittedFinancialTransactions(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        // Local inspection shares this bound snapshot and its guard; it never starts another transaction.
        this.dataset = new JdbcGenesisDatasetInspection(jdbc);
    }

    @Override
    public List<FinancialTransaction> listCommitted() {
        LedgerJdbcTransaction.requireCommittedSnapshot(jdbc);
        // Reuse the established GENESIS/supply/linkage/position checks, including truly empty datasets.
        if (dataset.findCommitted().isEmpty()) {
            return List.of();
        }
        // One joined read for every complete entry set; no per-transaction SELECT or hidden row limit.
        return jdbc.query("""
                SELECT f.id, f.reference, f.type, f.amount_vnd, f.currency, f.commit_seq, f.committed_at,
                       isfinite(f.committed_at) AS finite_time,
                       e.id AS entry_id, e.account_id, e.delta_vnd
                FROM public.financial_transaction f
                LEFT JOIN public.ledger_entry e ON e.transaction_id = f.id
                ORDER BY f.commit_seq DESC, e.delta_vnd, e.id
                """, FinancialTransactionRows::readAll);
    }
}
