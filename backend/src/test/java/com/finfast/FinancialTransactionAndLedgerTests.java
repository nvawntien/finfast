package com.finfast;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialTransactionAndLedgerTests extends RollbackDatabaseTest {

    @ParameterizedTest
    @ValueSource(strings = {"INTERNAL_TRANSFER", "DEMO_ALLOCATION", "GENESIS"})
    void acceptsSupportedTransactionTypes(String type) {
        UUID transaction = fixtures.transaction("type", type);
        assertThat(jdbc.queryForObject("SELECT type FROM financial_transaction WHERE id = ?", String.class, transaction))
                .isEqualTo(type);
    }

    @Test
    void rejectsDuplicateTransactionReference() {
        fixtures.transaction("reference", "SAME-REFERENCE");
        assertViolation("23505", "uq_financial_transaction_reference",
                () -> fixtures.transaction("reference", "SAME-REFERENCE"));
    }

    @Test
    void rejectsDuplicateCommitSequence() {
        fixtures.transaction("commit_seq", 1L);
        assertViolation("23505", "uq_financial_transaction_commit_seq", () -> fixtures.transaction("commit_seq", 1L));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonpositiveAmount(long amount) {
        assertViolation("23514", "ck_financial_transaction_amount_positive", () -> fixtures.transaction("amount_vnd", amount));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonpositiveCommitSequence(long sequence) {
        assertViolation("23514", "ck_financial_transaction_commit_seq_positive", () -> fixtures.transaction("commit_seq", sequence));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankReference(String reference) {
        assertViolation("23514", "ck_financial_transaction_reference_nonblank", () -> fixtures.transaction("reference", reference));
    }

    @Test
    void rejectsUnsupportedTransactionType() {
        assertViolation("23514", "ck_financial_transaction_type", () -> fixtures.transaction("type", "REVERSAL"));
    }

    @Test
    void rejectsNonVndTransaction() {
        assertViolation("23514", "ck_financial_transaction_currency", () -> fixtures.transaction("currency", "USD"));
    }

    @Test
    void rejectsSecondGenesis() {
        fixtures.transaction("type", "GENESIS");
        assertViolation("23505", "uq_single_genesis", () -> fixtures.transaction("type", "GENESIS"));
    }

    @ParameterizedTest
    @ValueSource(longs = {-100, 100})
    void acceptsSignedNonzeroLedgerDelta(long delta) {
        UUID entry = fixtures.entry(fixtures.transaction(), fixtures.account(), delta);
        assertThat(jdbc.queryForObject("SELECT delta_vnd FROM ledger_entry WHERE id = ?", Long.class, entry)).isEqualTo(delta);
    }

    @Test
    void rejectsZeroDelta() {
        UUID transaction = fixtures.transaction();
        UUID account = fixtures.account();
        assertViolation("23514", "ck_ledger_entry_delta_nonzero", () -> fixtures.entry(transaction, account, 0));
    }

    @Test
    void rejectsUnknownTransaction() {
        UUID account = fixtures.account();
        assertViolation("23503", "fk_ledger_entry_transaction", () -> fixtures.entry(DatabaseFixtures.UNKNOWN_ID, account, 100));
    }

    @Test
    void rejectsUnknownAccount() {
        UUID transaction = fixtures.transaction();
        assertViolation("23503", "fk_ledger_entry_account", () -> fixtures.entry(transaction, DatabaseFixtures.UNKNOWN_ID, 100));
    }

    @Test
    void rejectsDuplicateTransactionAccountPair() {
        UUID transaction = fixtures.transaction();
        UUID account = fixtures.account();
        fixtures.entry(transaction, account, 100);
        assertViolation("23505", "uq_ledger_entry_transaction_account", () -> fixtures.entry(transaction, account, -100));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("forbiddenMutations")
    void rejectsFinancialFactMutationEvenWhenNoRowsMatch(String table, String operation) {
        UUID transaction = fixtures.transaction();
        fixtures.entry(transaction, fixtures.account(), 100);
        String sql = switch (operation) {
            case "UPDATE" -> "UPDATE public." + table + " SET id = id";
            case "DELETE" -> "DELETE FROM public." + table;
            case "TRUNCATE" -> "TRUNCATE public." + table + " CASCADE";
            case "UPDATE EMPTY" -> "UPDATE public." + table + " SET id = id WHERE FALSE";
            case "DELETE EMPTY" -> "DELETE FROM public." + table + " WHERE FALSE";
            default -> throw new IllegalArgumentException(operation);
        };
        // CASCADE includes referencing tables, allowing the fact trigger to be reached.
        assertRejected(sql, "55000", "on public." + table + " is not allowed: financial facts are append-only");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public." + table, Long.class)).isEqualTo(1);
    }

    private static Stream<Arguments> forbiddenMutations() {
        return Stream.of("financial_transaction", "ledger_entry").flatMap(table ->
                Stream.of("UPDATE", "DELETE", "TRUNCATE", "UPDATE EMPTY", "DELETE EMPTY")
                        .map(operation -> Arguments.of(table, operation)));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void nonCascadeTransactionTruncateIsRejectedByForeignKeysBeforeTrigger() throws SQLException {
        // Hikari treats 0A000 as fatal and evicts the connection. Use a disposable
        // physical connection so its rollback does not depend on pool policy.
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                assertThatThrownBy(() -> statement.execute("TRUNCATE public.financial_transaction"))
                        .isInstanceOfSatisfying(SQLException.class, cause -> {
                            assertThat(cause.getSQLState()).isEqualTo("0A000");
                            assertThat(cause.getMessage()).contains("foreign key constraint");
                        });
            } finally {
                connection.rollback();
            }
        }
    }
}
