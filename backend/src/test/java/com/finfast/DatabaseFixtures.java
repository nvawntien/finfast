package com.finfast;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Test-only row builders, not posting/bootstrap logic. IDs reset for each isolated test.
final class DatabaseFixtures {

    static final OffsetDateTime RECORDED_AT = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    static final UUID UNKNOWN_ID = new UUID(0, Long.MAX_VALUE);

    private final JdbcTemplate jdbc;
    private long nextId;

    DatabaseFixtures(JdbcTemplate jdbc) {
        this(jdbc, 1);
    }

    DatabaseFixtures(JdbcTemplate jdbc, long firstId) {
        this.jdbc = jdbc;
        this.nextId = firstId;
    }

    UUID id() {
        return new UUID(0, nextId++);
    }

    UUID user(Object... overrides) {
        Map<String, Object> row = row(overrides);
        defaultValue(row, "id", id());
        defaultValue(row, "email_normalized", row.get("id") + "@example.test");
        defaultValue(row, "password_hash", "test-only-placeholder");
        return insert("app_user", row);
    }

    UUID customer(Object... overrides) {
        Map<String, Object> row = row(overrides);
        defaultValue(row, "id", id());
        if (!row.containsKey("user_id")) {
            row.put("user_id", user());
        }
        defaultValue(row, "display_name", "Synthetic customer");
        return insert("customer", row);
    }

    UUID account(Object... overrides) {
        Map<String, Object> row = row(overrides);
        UUID id = id();
        defaultValue(row, "id", id);
        defaultValue(row, "account_number", "ACC-" + id.getLeastSignificantBits());
        defaultValue(row, "account_type", "CUSTOMER");
        if (!row.containsKey("customer_id")) {
            row.put("customer_id", "TREASURY".equals(row.get("account_type")) ? null : customer());
        }
        return insert("account", row);
    }

    UUID transaction(Object... overrides) {
        Map<String, Object> row = row(overrides);
        UUID id = id();
        defaultValue(row, "id", id);
        defaultValue(row, "reference", "TX-" + id);
        defaultValue(row, "type", "INTERNAL_TRANSFER");
        defaultValue(row, "amount_vnd", 100L);
        defaultValue(row, "currency", "VND");
        defaultValue(row, "commit_seq", id.getLeastSignificantBits());
        defaultValue(row, "committed_at", RECORDED_AT);
        return insert("financial_transaction", row);
    }

    UUID entry(UUID transactionId, UUID accountId, long delta) {
        return insert("ledger_entry", row("id", id(), "transaction_id", transactionId,
                "account_id", accountId, "delta_vnd", delta));
    }

    DatasetReferences genesis(long sequence) {
        return new DatasetReferences(account("account_type", "TREASURY"),
                transaction("type", "GENESIS", "commit_seq", sequence));
    }

    DatasetReferences dataset(long sequence) {
        DatasetReferences references = genesis(sequence);
        dataset(references, "last_commit_seq", sequence);
        return references;
    }

    void dataset(DatasetReferences references, Object... overrides) {
        Map<String, Object> row = row(overrides);
        defaultValue(row, "singleton_id", (short) 1);
        defaultValue(row, "treasury_account_id", references.treasuryId());
        defaultValue(row, "genesis_tx_id", references.genesisId());
        defaultValue(row, "initial_supply_vnd", 100L);
        defaultValue(row, "last_commit_seq", 1L);
        insert("dataset_control", row);
    }

    UUID outcome(Object... overrides) {
        Map<String, Object> row = row(overrides);
        defaultValue(row, "id", id());
        if (!row.containsKey("initiator_user_id")) {
            row.put("initiator_user_id", user());
        }
        defaultValue(row, "operation_type", "INTERNAL_TRANSFER");
        defaultValue(row, "idempotency_key", "test-key");
        defaultValue(row, "canonicalization_version", (short) 1);
        defaultValue(row, "canonical_intent", "{\"amountVnd\":\"100\",\"currency\":\"VND\"}");
        defaultValue(row, "payload_fingerprint", "a".repeat(64));
        defaultValue(row, "final_kind", "REJECTED");
        defaultValue(row, "response_snapshot", "{\"outcome\":\"REJECTED\"}");
        defaultValue(row, "rejection_code", "REJECTED".equals(row.get("final_kind")) ? "INSUFFICIENT_FUNDS" : null);
        defaultValue(row, "created_at", RECORDED_AT);
        return insert("request_outcome", row);
    }

    UUID audit(Object... overrides) {
        Map<String, Object> row = row(overrides);
        defaultValue(row, "id", id());
        if (!row.containsKey("admin_user_id")) {
            row.put("admin_user_id", user("role", "ADMIN"));
        }
        if (!row.containsKey("target_account_id")) {
            row.put("target_account_id", account());
        }
        defaultValue(row, "action", "FREEZE_ACCOUNT");
        defaultValue(row, "reason", "Synthetic audit reason");
        defaultValue(row, "outcome", "SUCCESS");
        defaultValue(row, "before_status", "ACTIVE");
        defaultValue(row, "after_status", "FROZEN");
        defaultValue(row, "created_at", RECORDED_AT);
        return insert("admin_audit_event", row);
    }

    private UUID insert(String table, Map<String, Object> row) {
        String parameters = row.keySet().stream()
                .map(column -> column.equals("canonical_intent") || column.equals("response_snapshot")
                        ? "?::jsonb" : "?")
                .collect(Collectors.joining(", "));
        jdbc.update("INSERT INTO public." + table + " (" + String.join(", ", row.keySet())
                + ") VALUES (" + parameters + ")", row.values().toArray());
        return (UUID) row.get("id");
    }

    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            row.put((String) pairs[i], pairs[i + 1]);
        }
        return row;
    }

    private static void defaultValue(Map<String, Object> row, String column, Object value) {
        if (!row.containsKey(column)) {
            row.put(column, value);
        }
    }

    record DatasetReferences(UUID treasuryId, UUID genesisId) {
    }
}
