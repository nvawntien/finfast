package com.finfast.ledger.internal.adapter.outbound.persistence;

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
public class JdbcPostingPositionAllocator {

    private final JdbcTemplate jdbc;

    public JdbcPostingPositionAllocator(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public long reserveNext() {
        LedgerJdbcTransaction.require(jdbc, true);
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
}
