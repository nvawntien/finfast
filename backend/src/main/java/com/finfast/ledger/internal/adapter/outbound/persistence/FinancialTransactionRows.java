package com.finfast.ledger.internal.adapter.outbound.persistence;

import com.finfast.common.money.VndAmount;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Entry;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Shared reconstruction for joined rows grouped by transaction, with entries in deterministic order. */
final class FinancialTransactionRows {
    private FinancialTransactionRows() { }

    static List<FinancialTransaction> readAll(ResultSet rows) throws SQLException {
        var transactions = new ArrayList<FinancialTransaction>();
        try {
            boolean more = rows.next();
            while (more) {
                if (!"VND".equals(rows.getString("currency"))) {
                    throw new IllegalArgumentException("Unsupported persisted currency");
                }
                if (!rows.getBoolean("finite_time")) {
                    throw new IllegalArgumentException("Nonfinite persisted timestamp");
                }
                UUID id = rows.getObject("id", UUID.class);
                String reference = rows.getString("reference");
                if (!StandardCharsets.UTF_8.newEncoder().canEncode(reference)) {
                    throw new IllegalArgumentException("Invalid persisted reference encoding");
                }
                Type type = Type.valueOf(rows.getString("type"));
                VndAmount amount = new VndAmount(rows.getLong("amount_vnd"));
                long sequence = rows.getLong("commit_seq");
                var recordedAt = rows.getObject("committed_at", OffsetDateTime.class).toInstant();
                List<Entry> entries = new ArrayList<>();
                do {
                    UUID entryId = rows.getObject("entry_id", UUID.class);
                    if (entryId != null) {
                        entries.add(new Entry(entryId, rows.getObject("account_id", UUID.class), rows.getLong("delta_vnd")));
                    }
                    more = rows.next();
                } while (more && id.equals(rows.getObject("id", UUID.class)));
                if (type == Type.GENESIS && entries.size() != 1) {
                    throw new IllegalArgumentException("Incomplete GENESIS facts");
                }
                // Domain factories remain the sole authority for local cardinality/sign/magnitude invariants.
                transactions.add(switch (type) {
                    case INTERNAL_TRANSFER -> FinancialTransaction.internalTransfer(id, reference, amount, sequence, recordedAt, entries);
                    case DEMO_ALLOCATION -> FinancialTransaction.demoAllocation(id, reference, amount, sequence, recordedAt, entries);
                    case GENESIS -> FinancialTransaction.genesis(id, reference, amount, sequence, recordedAt,
                            entries.getFirst().accountId(), entries);
                });
            }
            return List.copyOf(transactions);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new DataIntegrityViolationException("Invalid or incomplete persisted Ledger facts", error);
        }
    }
}
