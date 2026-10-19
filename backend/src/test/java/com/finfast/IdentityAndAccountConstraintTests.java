package com.finfast;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityAndAccountConstraintTests extends RollbackDatabaseTest {

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "ADMIN"})
    void acceptsSupportedUserRoles(String role) {
        UUID user = fixtures.user("role", role);
        assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE id = ?", String.class, user))
                .isEqualTo(role);
    }

    @Test
    void defaultsUserRoleAndCreationTimestamp() {
        UUID user = fixtures.user();
        assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE id = ?", String.class, user))
                .isEqualTo("CUSTOMER");
        assertThat(jdbc.queryForObject("SELECT created_at FROM app_user WHERE id = ?", OffsetDateTime.class, user))
                .isEqualTo(jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class));
    }

    @Test
    void rejectsDuplicateStoredEmail() {
        fixtures.user("email_normalized", "same@example.test");
        assertViolation("23505", "uq_app_user_email", () -> fixtures.user("email_normalized", "same@example.test"));
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankEmail(String email) {
        assertViolation(email == null ? "23502" : "23514", email == null ? "email_normalized" : "ck_app_user_email_nonblank",
                () -> fixtures.user("email_normalized", email));
    }

    @Test
    void rejectsUnsupportedRole() {
        assertViolation("23514", "ck_app_user_role", () -> fixtures.user("role", "ROOT"));
    }

    @Test
    void rejectsNullPasswordHash() {
        assertViolation("23502", "password_hash", () -> fixtures.user("password_hash", null));
    }

    @Test
    void createsCustomerWithExistingUser() {
        UUID user = fixtures.user();
        UUID customer = fixtures.customer("user_id", user);
        assertThat(jdbc.queryForObject("SELECT user_id FROM customer WHERE id = ?", UUID.class, customer))
                .isEqualTo(user);
    }

    @Test
    void rejectsDuplicateCustomerForUser() {
        UUID user = fixtures.user();
        fixtures.customer("user_id", user);
        assertViolation("23505", "uq_customer_user", () -> fixtures.customer("user_id", user));
    }

    @Test
    void rejectsUnknownCustomerUser() {
        assertViolation("23503", "fk_customer_user", () -> fixtures.customer("user_id", DatabaseFixtures.UNKNOWN_ID));
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankDisplayName(String name) {
        assertViolation(name == null ? "23502" : "23514", name == null ? "display_name" : "ck_customer_display_name_nonblank",
                () -> fixtures.customer("display_name", name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "FROZEN"})
    void acceptsCustomerAccountStatesAndDefaultZeroBalance(String status) {
        UUID account = fixtures.account("status", status);
        assertThat(jdbc.queryForObject("SELECT balance_vnd FROM account WHERE id = ?", Long.class, account)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM account WHERE id = ?", String.class, account)).isEqualTo(status);
        assertThat(jdbc.queryForObject("SELECT currency FROM account WHERE id = ?", String.class, account)).isEqualTo("VND");
        assertThat(jdbc.queryForObject("SELECT customer_id FROM account WHERE id = ?", UUID.class, account)).isNotNull();
    }

    @Test
    void acceptsSystemOwnedActiveTreasury() {
        UUID treasury = fixtures.account("account_type", "TREASURY");
        assertThat(jdbc.queryForObject("SELECT customer_id FROM account WHERE id = ?", UUID.class, treasury)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM account WHERE id = ?", String.class, treasury)).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsDuplicateAccountNumber() {
        fixtures.account("account_number", "DUPLICATE");
        assertViolation("23505", "uq_account_number", () -> fixtures.account("account_number", "DUPLICATE"));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankAccountNumber(String number) {
        assertViolation("23514", "ck_account_number_nonblank", () -> fixtures.account("account_number", number));
    }

    @Test
    void rejectsDuplicateAccountOwnership() {
        UUID customer = fixtures.customer();
        fixtures.account("customer_id", customer);
        assertViolation("23505", "uq_account_customer", () -> fixtures.account("customer_id", customer));
    }

    @Test
    void rejectsCustomerAccountWithoutOwner() {
        assertViolation("23514", "ck_account_ownership", () -> fixtures.account("customer_id", null));
    }

    @Test
    void rejectsTreasuryWithCustomerOwner() {
        UUID customer = fixtures.customer();
        assertViolation("23514", "ck_account_ownership",
                () -> fixtures.account("account_type", "TREASURY", "customer_id", customer));
    }

    @Test
    void rejectsSecondTreasury() {
        fixtures.account("account_type", "TREASURY");
        assertViolation("23505", "uq_account_single_treasury", () -> fixtures.account("account_type", "TREASURY"));
    }

    @Test
    void rejectsFrozenTreasury() {
        assertViolation("23514", "ck_account_treasury_active",
                () -> fixtures.account("account_type", "TREASURY", "status", "FROZEN"));
    }

    @Test
    void rejectsUnsupportedAccountType() {
        // An unknown type also violates the owner/type shape; either CHECK may reject first.
        assertViolation("23514", "ck_account_", () -> fixtures.account("account_type", "SAVINGS"));
    }

    @Test
    void rejectsUnsupportedAccountStatus() {
        assertViolation("23514", "ck_account_status", () -> fixtures.account("status", "CLOSED"));
    }

    @Test
    void rejectsNonVndAccount() {
        assertViolation("23514", "ck_account_currency", () -> fixtures.account("currency", "USD"));
    }

    @Test
    void rejectsNegativeBalance() {
        assertViolation("23514", "ck_account_balance_nonnegative", () -> fixtures.account("balance_vnd", -1L));
    }

    @Test
    void rejectsUnknownAccountCustomer() {
        assertViolation("23503", "fk_account_customer", () -> fixtures.account("customer_id", DatabaseFixtures.UNKNOWN_ID));
    }
}
