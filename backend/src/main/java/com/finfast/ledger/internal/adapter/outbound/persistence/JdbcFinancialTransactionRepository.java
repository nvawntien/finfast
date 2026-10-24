package com.finfast.ledger.internal.adapter.outbound.persistence;

import com.finfast.ledger.internal.application.port.outbound.FinancialTransactionStore;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Entry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ledger-internal immutable fact storage, not a posting capability or commit acknowledgement.
 * Register as a Spring bean when wiring a Ledger use case; calls must cross its transaction proxy.
 * Phase 2 does not install a public provider or an automatic startup writer.
 */
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public class JdbcFinancialTransactionRepository implements FinancialTransactionStore {

    private final JdbcTemplate jdbc;

    public JdbcFinancialTransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    /** All entries are already validated by the immutable domain constructor; failures abort the caller's transaction. */
    @Override
    public void insert(FinancialTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        LedgerJdbcTransaction.require(jdbc, true);
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(transaction.reference())) {
            throw new IllegalArgumentException("Ledger reference must contain valid Unicode");
        }
        // PostgreSQL stores microseconds. Explicitly discard sub-microsecond precision, never change the domain object.
        OffsetDateTime recordedAt = transaction.recordedAt().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        requireOne(jdbc.update("""
                INSERT INTO public.financial_transaction
                    (id, reference, type, amount_vnd, currency, commit_seq, committed_at)
                VALUES (?, ?, ?, ?, 'VND', ?, ?)
                """, transaction.id(), transaction.reference(), transaction.type().name(),
                transaction.amount().value(), transaction.commitSeq(), recordedAt));
        for (Entry entry : transaction.entries()) {
            requireOne(jdbc.update("""
                    INSERT INTO public.ledger_entry (id, transaction_id, account_id, delta_vnd)
                    VALUES (?, ?, ?, ?)
                    """, entry.id(), transaction.id(), entry.accountId(), entry.deltaVnd()));
        }
    }

    /** One SQL snapshot reads the header and complete entry set. Pending writes in the caller's transaction are visible. */
    public Optional<FinancialTransaction> findById(UUID transactionId) {
        Objects.requireNonNull(transactionId, "transactionId");
        LedgerJdbcTransaction.require(jdbc, false);
        return jdbc.query("""
                SELECT f.id, f.reference, f.type, f.amount_vnd, f.currency, f.commit_seq, f.committed_at,
                       isfinite(f.committed_at) AS finite_time,
                       e.id AS entry_id, e.account_id, e.delta_vnd
                FROM public.financial_transaction f
                LEFT JOIN public.ledger_entry e ON e.transaction_id = f.id
                WHERE f.id = ?
                ORDER BY e.delta_vnd, e.id
                """, (ResultSetExtractor<Optional<FinancialTransaction>>) rows ->
                        FinancialTransactionRows.readAll(rows).stream().findFirst(), transactionId);
    }

    private static void requireOne(int count) {
        if (count != 1) {
            throw new DataIntegrityViolationException("Expected exactly one inserted Ledger row");
        }
    }
}
