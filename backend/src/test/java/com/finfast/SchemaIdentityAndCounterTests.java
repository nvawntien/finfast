package com.finfast;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Tag("integration")
@Import(PostgresIntegrationTests.ContainerConfiguration.class)
@Transactional
class SchemaIdentityAndCounterTests {

    private final JdbcTemplate jdbc;

    @Autowired
    SchemaIdentityAndCounterTests(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "account_number", "account_type", "customer_id"})
    void rejectsAccountIdentityAndOwnershipChanges(String column) {
        UUID accountId = createCustomerAccount();
        Object replacement = switch (column) {
            case "id" -> UUID.randomUUID();
            case "account_number" -> "CHANGED-NUMBER";
            case "account_type" -> "TREASURY";
            case "customer_id" -> createCustomer();
            default -> throw new IllegalArgumentException(column);
        };

        assertRejected("UPDATE public.account SET " + column + " = ? WHERE id = ?",
                "55000", "Account identity and ownership are immutable", replacement, accountId);
    }

    @Test
    void rejectsClearingAccountOwnership() {
        UUID accountId = createCustomerAccount();

        assertRejected("UPDATE public.account SET customer_id = NULL WHERE id = ?",
                "55000", "Account identity and ownership are immutable", accountId);
    }

    @Test
    void allowsBalanceAndStatusUpdates() {
        UUID accountId = createCustomerAccount();

        assertThat(jdbc.update("UPDATE public.account SET balance_vnd = 100 WHERE id = ?", accountId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT balance_vnd FROM public.account WHERE id = ?",
                Long.class, accountId)).isEqualTo(100L);
        for (String status : new String[]{"FROZEN", "ACTIVE"}) {
            assertThat(jdbc.update("UPDATE public.account SET status = ? WHERE id = ?", status, accountId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM public.account WHERE id = ?",
                    String.class, accountId)).isEqualTo(status);
        }
    }

    @Test
    void allowsExactlyOneCounterIncrement() {
        createDataset(10);

        assertThat(jdbc.update("UPDATE public.dataset_control SET last_commit_seq = last_commit_seq + 1"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_commit_seq FROM public.dataset_control", Long.class))
                .isEqualTo(11L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 9, 10, 12})
    void rejectsCounterResetDecreaseNoOpAndJump(long nextSequence) {
        createDataset(10);

        assertRejected("UPDATE public.dataset_control SET last_commit_seq = ?",
                "23514", "Dataset last_commit_seq must increase by exactly 1", nextSequence);
    }

    @ParameterizedTest
    @ValueSource(strings = {"singleton_id", "treasury_account_id", "genesis_tx_id", "initial_supply_vnd"})
    void rejectsDatasetConfigurationChangesEvenWithValidCounterIncrement(String column) {
        createDataset(1);
        Object replacement = switch (column) {
            case "singleton_id" -> (short) 2;
            case "initial_supply_vnd" -> 2L;
            case "treasury_account_id", "genesis_tx_id" -> UUID.randomUUID();
            default -> throw new IllegalArgumentException(column);
        };

        assertRejected("UPDATE public.dataset_control SET " + column
                        + " = ?, last_commit_seq = last_commit_seq + 1",
                "55000", "Dataset identity, Treasury, GENESIS and initial supply are immutable", replacement);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 2})
    void rejectsBootstrapCounterThatDoesNotMatchGenesis(long initialSequence) {
        DatasetReferences references = createGenesis(1);

        assertRejected("""
                INSERT INTO public.dataset_control
                    (singleton_id, treasury_account_id, genesis_tx_id, initial_supply_vnd, last_commit_seq)
                VALUES (1, ?, ?, 1, ?)
                """, "23514", "Dataset last_commit_seq must match the GENESIS commit_seq",
                references.treasuryId(), references.genesisId(), initialSequence);
    }

    @Test
    void allowsLastRepresentableCounterIncrement() {
        createDataset(Long.MAX_VALUE - 1);

        assertThat(jdbc.update("UPDATE public.dataset_control SET last_commit_seq = last_commit_seq + 1"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_commit_seq FROM public.dataset_control", Long.class))
                .isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, Long.MAX_VALUE})
    void rejectsUpdatesWhenCounterIsExhaustedWithoutBigintOverflow(long nextSequence) {
        createDataset(Long.MAX_VALUE);

        assertRejected("UPDATE public.dataset_control SET last_commit_seq = ?",
                "23514", "Dataset last_commit_seq is exhausted", nextSequence);
    }

    private UUID createCustomer() {
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        jdbc.update("INSERT INTO public.app_user (id, email_normalized, password_hash) VALUES (?, ?, ?)",
                userId, userId + "@example.test", "test-only-placeholder");
        jdbc.update("INSERT INTO public.customer (id, user_id, display_name) VALUES (?, ?, ?)",
                customerId, userId, "Synthetic test customer");
        return customerId;
    }

    private UUID createCustomerAccount() {
        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.account (id, account_number, customer_id, account_type)
                VALUES (?, 'TEST-CUSTOMER', ?, 'CUSTOMER')
                """, accountId, createCustomer());
        return accountId;
    }

    private DatasetReferences createGenesis(long commitSequence) {
        UUID treasuryId = UUID.randomUUID();
        UUID genesisId = UUID.randomUUID();
        jdbc.update("INSERT INTO public.account (id, account_number, account_type) VALUES (?, ?, 'TREASURY')",
                treasuryId, "TEST-TREASURY");
        jdbc.update("""
                INSERT INTO public.financial_transaction
                    (id, reference, type, amount_vnd, currency, commit_seq, committed_at)
                VALUES (?, 'TEST-GENESIS', 'GENESIS', 1, 'VND', ?, CURRENT_TIMESTAMP)
                """, genesisId, commitSequence);
        return new DatasetReferences(treasuryId, genesisId);
    }

    private void createDataset(long commitSequence) {
        DatasetReferences references = createGenesis(commitSequence);
        jdbc.update("""
                INSERT INTO public.dataset_control
                    (singleton_id, treasury_account_id, genesis_tx_id, initial_supply_vnd, last_commit_seq)
                VALUES (1, ?, ?, 1, ?)
                """, references.treasuryId(), references.genesisId(), commitSequence);
    }

    // Each rejected statement ends its test transaction; Spring rolls all fixtures back.
    private void assertRejected(String sql, String sqlState, String message, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args))
                .isInstanceOfSatisfying(DataAccessException.class, error ->
                        assertThat(error.getMostSpecificCause())
                                .isInstanceOfSatisfying(SQLException.class, cause -> {
                                    assertThat(cause.getSQLState()).isEqualTo(sqlState);
                                    assertThat(cause.getMessage()).contains(message);
                                }));
    }

    private record DatasetReferences(UUID treasuryId, UUID genesisId) {
    }
}
