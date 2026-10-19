package com.finfast;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestOutcomeConstraintTests extends RollbackDatabaseTest {

    @Test
    void acceptsCompletedOutcomeLinkedToTransaction() {
        UUID transaction = fixtures.transaction();
        UUID outcome = fixtures.outcome("final_kind", "COMPLETED", "transaction_id", transaction,
                "response_snapshot", "{\"outcome\":\"COMPLETED\",\"reference\":\"test-reference\"}");
        assertThat(jdbc.queryForObject("SELECT transaction_id FROM request_outcome WHERE id = ?", UUID.class, outcome))
                .isEqualTo(transaction);
        assertThat(jdbc.queryForObject("SELECT rejection_code FROM request_outcome WHERE id = ?", String.class, outcome)).isNull();
    }

    @Test
    void acceptsRejectedOutcomeAndReadsJsonbWithoutDependingOnPropertyOrder() {
        UUID outcome = fixtures.outcome("response_snapshot", "{\"outcome\":\"REJECTED\",\"code\":\"INSUFFICIENT_FUNDS\"}");
        assertThat(jdbc.queryForObject("SELECT transaction_id FROM request_outcome WHERE id = ?", UUID.class, outcome)).isNull();
        assertThat(jdbc.queryForObject("SELECT canonical_intent->>'amountVnd' FROM request_outcome WHERE id = ?", String.class, outcome))
                .isEqualTo("100");
        assertThat(jdbc.queryForObject("SELECT response_snapshot->>'code' FROM request_outcome WHERE id = ?", String.class, outcome))
                .isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(jdbc.queryForObject("""
                SELECT canonical_intent = '{"currency":"VND","amountVnd":"100"}'::jsonb
                FROM request_outcome WHERE id = ?
                """, Boolean.class, outcome)).isTrue();
    }

    @Test
    void rejectsDuplicateCompleteScope() {
        UUID user = fixtures.user();
        fixtures.outcome("initiator_user_id", user);
        assertViolation("23505", "uq_request_outcome_scope", () -> fixtures.outcome("initiator_user_id", user));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM request_outcome", Long.class)).isEqualTo(1);
    }

    @Test
    void allowsSameKeyAcrossUsersAndOperations() {
        UUID user = fixtures.user();
        fixtures.outcome("initiator_user_id", user);
        fixtures.outcome("initiator_user_id", fixtures.user());
        fixtures.outcome("initiator_user_id", user, "operation_type", "DEMO_ALLOCATION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM request_outcome WHERE idempotency_key = 'test-key'", Long.class))
                .isEqualTo(3);
    }

    @Test
    void preservesCaseAndWhitespaceInOpaqueKeys() {
        UUID user = fixtures.user();
        for (String key : new String[]{"Key", "key", " key "}) {
            fixtures.outcome("initiator_user_id", user, "idempotency_key", key);
        }
        assertThat(jdbc.queryForList("SELECT idempotency_key FROM request_outcome ORDER BY idempotency_key COLLATE \"C\"",
                String.class)).containsExactly(" key ", "Key", "key");
    }

    @Test
    void acceptsMaximumLengthKeyWithoutTruncation() {
        String key = "k".repeat(200);
        UUID outcome = fixtures.outcome("idempotency_key", key);
        assertThat(jdbc.queryForObject("SELECT idempotency_key FROM request_outcome WHERE id = ?", String.class, outcome)).isEqualTo(key);
    }

    @Test
    void rejectsEmptyKey() {
        assertViolation("23514", "ck_request_outcome_key_nonempty", () -> fixtures.outcome("idempotency_key", ""));
    }

    @Test
    void rejectsOverlongKeyInsteadOfTruncating() {
        assertViolation("22001", "character varying(200)", () -> fixtures.outcome("idempotency_key", "k".repeat(201)));
    }

    @Test
    void rejectsUnsupportedOperation() {
        assertViolation("23514", "ck_request_outcome_operation", () -> fixtures.outcome("operation_type", "LOGIN"));
    }

    @ParameterizedTest
    @ValueSource(shorts = {0, -1})
    void rejectsNonpositiveCanonicalizationVersion(short version) {
        assertViolation("23514", "ck_request_outcome_canonicalization_positive", () -> fixtures.outcome("canonicalization_version", version));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 63, 65})
    void rejectsInvalidFingerprintLength(int length) {
        // >64 is rejected by VARCHAR before the CHECK; neither path accepts it.
        assertViolation(length > 64 ? "22001" : "23514", length > 64 ? "character varying(64)" : "ck_request_outcome_fingerprint",
                () -> fixtures.outcome("payload_fingerprint", "a".repeat(length)));
    }

    @Test
    void rejectsNonhexFingerprint() {
        assertViolation("23514", "ck_request_outcome_fingerprint", () -> fixtures.outcome("payload_fingerprint", "g".repeat(64)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROCESSING", "UNKNOWN", "FAILED"})
    void rejectsNonfinalKinds(String kind) {
        // Unsupported kinds also violate the final-shape CHECK; its name sorts first.
        assertViolation("23514", "ck_request_outcome_final_", () -> fixtures.outcome("final_kind", kind));
    }

    @Test
    void rejectsCompletedWithoutTransaction() {
        assertViolation("23514", "ck_request_outcome_final_shape", () -> fixtures.outcome("final_kind", "COMPLETED"));
    }

    @Test
    void rejectsCompletedWithRejectionCode() {
        UUID transaction = fixtures.transaction();
        assertViolation("23514", "ck_request_outcome_final_shape",
                () -> fixtures.outcome("final_kind", "COMPLETED", "transaction_id", transaction, "rejection_code", "DECLINED"));
    }

    @Test
    void rejectsRejectedWithTransaction() {
        UUID transaction = fixtures.transaction();
        assertViolation("23514", "ck_request_outcome_final_shape", () -> fixtures.outcome("transaction_id", transaction));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "\t "})
    void rejectsRejectedWithoutNonblankCode(String code) {
        assertViolation("23514", code == null ? "ck_request_outcome_final_shape" : "ck_request_outcome_rejection_nonblank",
                () -> fixtures.outcome("rejection_code", code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"canonical_intent", "response_snapshot"})
    void rejectsNullJsonb(String column) {
        assertViolation("23502", column, () -> fixtures.outcome(column, null));
    }

    @Test
    void rejectsUnknownInitiator() {
        assertViolation("23503", "fk_request_outcome_initiator",
                () -> fixtures.outcome("initiator_user_id", DatabaseFixtures.UNKNOWN_ID));
    }

    @Test
    void rejectsUnknownCompletedTransaction() {
        assertViolation("23503", "fk_request_outcome_transaction",
                () -> fixtures.outcome("final_kind", "COMPLETED", "transaction_id", DatabaseFixtures.UNKNOWN_ID));
    }
}
