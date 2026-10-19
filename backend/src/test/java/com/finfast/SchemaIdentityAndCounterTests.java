package com.finfast;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaIdentityAndCounterTests extends RollbackDatabaseTest {

    @ParameterizedTest
    @ValueSource(strings = {"id", "account_number", "account_type", "customer_id"})
    void rejectsAccountIdentityAndOwnershipChanges(String column) {
        UUID accountId = fixtures.account();
        Object replacement = switch (column) {
            case "id" -> fixtures.id();
            case "account_number" -> "CHANGED-NUMBER";
            case "account_type" -> "TREASURY";
            case "customer_id" -> fixtures.customer();
            default -> throw new IllegalArgumentException(column);
        };

        assertRejected("UPDATE public.account SET " + column + " = ? WHERE id = ?",
                "55000", "Account identity and ownership are immutable", replacement, accountId);
    }

    @Test
    void rejectsClearingAccountOwnership() {
        UUID accountId = fixtures.account();

        assertRejected("UPDATE public.account SET customer_id = NULL WHERE id = ?",
                "55000", "Account identity and ownership are immutable", accountId);
    }

    @Test
    void rejectsAssigningCustomerToTreasury() {
        UUID treasury = fixtures.account("account_type", "TREASURY");
        UUID customer = fixtures.customer();

        assertRejected("UPDATE public.account SET customer_id = ? WHERE id = ?",
                "55000", "Account identity and ownership are immutable", customer, treasury);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELETE FROM public.account", "TRUNCATE public.account CASCADE"})
    void rejectsAccountRemoval(String sql) {
        fixtures.account();

        assertRejected(sql, "55000", "account identities and numbers must be retained");
    }

    @Test
    void allowsBalanceAndStatusUpdates() {
        UUID accountId = fixtures.account();

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
        fixtures.dataset(10);

        assertThat(jdbc.update("UPDATE public.dataset_control SET last_commit_seq = last_commit_seq + 1"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_commit_seq FROM public.dataset_control", Long.class))
                .isEqualTo(11L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 9, 10, 12})
    void rejectsCounterResetDecreaseNoOpAndJump(long nextSequence) {
        fixtures.dataset(10);

        assertRejected("UPDATE public.dataset_control SET last_commit_seq = ?",
                "23514", "Dataset last_commit_seq must increase by exactly 1", nextSequence);
    }

    @ParameterizedTest
    @ValueSource(strings = {"singleton_id", "treasury_account_id", "genesis_tx_id", "initial_supply_vnd"})
    void rejectsDatasetConfigurationChangesEvenWithValidCounterIncrement(String column) {
        fixtures.dataset(1);
        Object replacement = switch (column) {
            case "singleton_id" -> (short) 2;
            case "initial_supply_vnd" -> 2L;
            case "treasury_account_id", "genesis_tx_id" -> fixtures.id();
            default -> throw new IllegalArgumentException(column);
        };

        assertRejected("UPDATE public.dataset_control SET " + column
                        + " = ?, last_commit_seq = last_commit_seq + 1",
                "55000", "Dataset identity, Treasury, GENESIS and initial supply are immutable", replacement);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 2})
    void rejectsBootstrapCounterThatDoesNotMatchGenesis(long initialSequence) {
        DatabaseFixtures.DatasetReferences references = fixtures.genesis(1);

        assertRejected("""
                INSERT INTO public.dataset_control
                    (singleton_id, treasury_account_id, genesis_tx_id, initial_supply_vnd, last_commit_seq)
                VALUES (1, ?, ?, 1, ?)
                """, "23514", "Dataset last_commit_seq must match the GENESIS commit_seq",
                references.treasuryId(), references.genesisId(), initialSequence);
    }

    @Test
    void allowsLastRepresentableCounterIncrement() {
        fixtures.dataset(Long.MAX_VALUE - 1);

        assertThat(jdbc.update("UPDATE public.dataset_control SET last_commit_seq = last_commit_seq + 1"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_commit_seq FROM public.dataset_control", Long.class))
                .isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, Long.MAX_VALUE})
    void rejectsUpdatesWhenCounterIsExhaustedWithoutBigintOverflow(long nextSequence) {
        fixtures.dataset(Long.MAX_VALUE);

        assertRejected("UPDATE public.dataset_control SET last_commit_seq = ?",
                "23514", "Dataset last_commit_seq is exhausted", nextSequence);
    }
}
