package com.finfast;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuditConstraintTests extends RollbackDatabaseTest {

    @Test
    void acceptsAllocationSuccessWithTransactionAndNoStatuses() {
        UUID transaction = fixtures.transaction("type", "DEMO_ALLOCATION");
        UUID audit = allocation(transaction);
        assertThat(jdbc.queryForObject("SELECT transaction_id FROM admin_audit_event WHERE id = ?", UUID.class, audit))
                .isEqualTo(transaction);
    }

    @Test
    void rejectsAllocationWithoutTransaction() {
        assertViolation("23514", "ck_admin_audit_shape", () -> allocation(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"before_status", "after_status"})
    void rejectsAllocationWithStatusFields(String column) {
        UUID transaction = fixtures.transaction("type", "DEMO_ALLOCATION");
        assertViolation("23514", "ck_admin_audit_shape", () -> fixtures.audit("action", "DEMO_ALLOCATION",
                "transaction_id", transaction, "before_status", null, "after_status", null, column, "ACTIVE"));
    }

    @Test
    void rejectsDuplicateAuditedTransaction() {
        UUID transaction = fixtures.transaction("type", "DEMO_ALLOCATION");
        allocation(transaction);
        assertViolation("23505", "uq_admin_audit_transaction", () -> allocation(transaction));
    }

    @Test
    void rejectsUnchangedAllocation() {
        UUID transaction = fixtures.transaction("type", "DEMO_ALLOCATION");
        assertViolation("23514", "ck_admin_audit_shape", () -> fixtures.audit("action", "DEMO_ALLOCATION",
                "outcome", "UNCHANGED", "transaction_id", transaction, "before_status", null, "after_status", null));
    }

    @ParameterizedTest
    @CsvSource({"FREEZE_ACCOUNT, ACTIVE, FROZEN", "UNFREEZE_ACCOUNT, FROZEN, ACTIVE"})
    void acceptsSuccessfulStatusTransitions(String action, String before, String after) {
        UUID audit = fixtures.audit("action", action, "before_status", before, "after_status", after);
        assertThat(jdbc.queryForObject("SELECT before_status FROM admin_audit_event WHERE id = ?", String.class, audit)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT after_status FROM admin_audit_event WHERE id = ?", String.class, audit)).isEqualTo(after);
        assertThat(jdbc.queryForObject("SELECT transaction_id FROM admin_audit_event WHERE id = ?", UUID.class, audit)).isNull();
    }

    @ParameterizedTest
    @CsvSource({"FREEZE_ACCOUNT, FROZEN, ACTIVE", "UNFREEZE_ACCOUNT, ACTIVE, FROZEN"})
    void rejectsReverseStatusTransitions(String action, String before, String after) {
        assertViolation("23514", "ck_admin_audit_shape",
                () -> fixtures.audit("action", action, "before_status", before, "after_status", after));
    }

    @ParameterizedTest
    @CsvSource({"FREEZE_ACCOUNT, ACTIVE, FROZEN", "UNFREEZE_ACCOUNT, FROZEN, ACTIVE"})
    void rejectsFinancialReferenceForStatusOperation(String action, String before, String after) {
        UUID transaction = fixtures.transaction();
        assertViolation("23514", "ck_admin_audit_shape", () -> fixtures.audit("action", action,
                "before_status", before, "after_status", after, "transaction_id", transaction));
    }

    @ParameterizedTest
    @CsvSource({"FREEZE_ACCOUNT, FROZEN", "UNFREEZE_ACCOUNT, ACTIVE"})
    void acceptsUnchangedEqualStatuses(String action, String status) {
        UUID audit = fixtures.audit("action", action, "outcome", "UNCHANGED", "before_status", status, "after_status", status);
        assertThat(jdbc.queryForObject("SELECT outcome FROM admin_audit_event WHERE id = ?", String.class, audit)).isEqualTo("UNCHANGED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"FREEZE_ACCOUNT", "UNFREEZE_ACCOUNT"})
    void rejectsUnchangedDifferentStatuses(String action) {
        assertViolation("23514", "ck_admin_audit_shape", () -> fixtures.audit("action", action, "outcome", "UNCHANGED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"before_status", "after_status"})
    void rejectsIncompleteStatusAudit(String column) {
        assertViolation("23514", "ck_admin_audit_shape", () -> fixtures.audit(column, null));
    }

    @Test
    void rejectsUnknownAdminUser() {
        assertViolation("23503", "fk_admin_audit_admin", () -> fixtures.audit("admin_user_id", DatabaseFixtures.UNKNOWN_ID));
    }

    @Test
    void rejectsUnknownTargetAccount() {
        assertViolation("23503", "fk_admin_audit_target", () -> fixtures.audit("target_account_id", DatabaseFixtures.UNKNOWN_ID));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankReason(String reason) {
        assertViolation("23514", "ck_admin_audit_reason_nonblank", () -> fixtures.audit("reason", reason));
    }

    @Test
    void rejectsUnsupportedAction() {
        assertViolation("23514", "ck_admin_audit_action", () -> fixtures.audit("action", "DELETE_ACCOUNT"));
    }

    @Test
    void rejectsUnsupportedOutcome() {
        assertViolation("23514", "ck_admin_audit_outcome", () -> fixtures.audit("outcome", "FAILED"));
    }

    private UUID allocation(UUID transaction) {
        return fixtures.audit("action", "DEMO_ALLOCATION", "transaction_id", transaction,
                "before_status", null, "after_status", null);
    }
}
