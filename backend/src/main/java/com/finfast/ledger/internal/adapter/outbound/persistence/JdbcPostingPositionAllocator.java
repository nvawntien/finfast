package com.finfast.ledger.internal.adapter.outbound.persistence;

import com.finfast.ledger.internal.application.port.outbound.PostingPositionAllocator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Internal TD-004 primitive, to be wired as a Spring bean behind the Ledger posting implementation.
 * Reserve only after future Accounts locking/validation, then persist complete facts in the same outer transaction.
 * A returned position is tentative, not a posting or commit acknowledgement. No retry, setup or independent commit.
 */
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public class JdbcPostingPositionAllocator implements PostingPositionAllocator {

    private final JdbcTemplate jdbc;

    public JdbcPostingPositionAllocator(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public long reserveNext() {
        LedgerJdbcTransaction.require(jdbc, true);
        requireCompleteGenesisIfPresent();
        var positions = jdbc.query("""
                UPDATE public.dataset_control
                SET last_commit_seq = last_commit_seq + 1
                WHERE singleton_id = 1 AND last_commit_seq < 9223372036854775807
                RETURNING last_commit_seq
                """, (row, number) -> row.getLong("last_commit_seq"));
        if (positions.size() != 1 || positions.getFirst() <= 0) {
            throw new DataIntegrityViolationException(
                    "Expected one available dataset posting position; singleton missing or counter exhausted");
        }
        return positions.getFirst();
    }

    /** Immutable Ledger-only bootstrap precondition; not balance reconciliation or Accounts validation. */
    private void requireCompleteGenesisIfPresent() {
        var ready = jdbc.query("""
                SELECT g.type = 'GENESIS' AND g.amount_vnd = d.initial_supply_vnd
                       AND d.last_commit_seq >= g.commit_seq
                       AND (SELECT count(*) FROM public.ledger_entry e WHERE e.transaction_id = g.id) = 1
                       AND EXISTS (SELECT 1 FROM public.ledger_entry e
                                   WHERE e.transaction_id = g.id AND e.account_id = d.treasury_account_id
                                     AND e.delta_vnd = d.initial_supply_vnd) AS ready
                FROM public.dataset_control d
                JOIN public.financial_transaction g ON g.id = d.genesis_tx_id
                WHERE d.singleton_id = 1
                """, (row, number) -> row.getBoolean("ready"));
        // Missing singleton/exhaustion retain the established UPDATE/row-count failure below.
        if (ready.size() > 1 || (!ready.isEmpty() && !ready.getFirst())) {
            throw new DataIntegrityViolationException("Incomplete GENESIS dataset cannot allocate a posting position");
        }
    }
}
