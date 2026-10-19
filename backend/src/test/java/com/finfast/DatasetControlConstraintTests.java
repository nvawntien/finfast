package com.finfast;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Counter increment/exhaustion and immutable UPDATE cases remain in SchemaIdentityAndCounterTests.
class DatasetControlConstraintTests extends RollbackDatabaseTest {

    @Test
    void acceptsTreasuryGenesisLinksAndOnlyOneControlRow() {
        DatabaseFixtures.DatasetReferences references = fixtures.dataset(1);
        assertThat(jdbc.queryForObject("SELECT treasury_account_id FROM dataset_control", UUID.class))
                .isEqualTo(references.treasuryId());
        assertThat(jdbc.queryForObject("SELECT genesis_tx_id FROM dataset_control", UUID.class))
                .isEqualTo(references.genesisId());
        assertViolation("23505", "pk_dataset_control", () -> fixtures.dataset(references));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dataset_control", Long.class)).isEqualTo(1);
    }

    @Test
    void rejectsNonSingletonId() {
        DatabaseFixtures.DatasetReferences references = fixtures.genesis(1);
        assertViolation("23514", "ck_dataset_control_singleton", () -> fixtures.dataset(references, "singleton_id", (short) 2));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonpositiveSupply(long supply) {
        DatabaseFixtures.DatasetReferences references = fixtures.genesis(1);
        assertViolation("23514", "ck_dataset_control_supply_positive", () -> fixtures.dataset(references, "initial_supply_vnd", supply));
    }

    @Test
    void rejectsNegativeInitialCounterBeforeRowCheck() {
        DatabaseFixtures.DatasetReferences references = fixtures.genesis(1);
        // The bootstrap consistency guard is reached before the nonnegative CHECK.
        assertViolation("23514", "Dataset last_commit_seq must match the GENESIS commit_seq",
                () -> fixtures.dataset(references, "last_commit_seq", -1L));
    }

    @Test
    void rejectsCustomerAsTreasuryReference() {
        UUID customerAccount = fixtures.account();
        UUID genesis = fixtures.transaction("type", "GENESIS", "commit_seq", 1L);
        assertViolation("23514", "must reference a TREASURY account",
                () -> fixtures.dataset(new DatabaseFixtures.DatasetReferences(customerAccount, genesis)));
    }

    @Test
    void rejectsNonGenesisReference() {
        UUID treasury = fixtures.account("account_type", "TREASURY");
        UUID transfer = fixtures.transaction("commit_seq", 1L);
        assertViolation("23514", "must reference a GENESIS transaction",
                () -> fixtures.dataset(new DatabaseFixtures.DatasetReferences(treasury, transfer)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELETE FROM public.dataset_control", "TRUNCATE public.dataset_control"})
    void rejectsControlRemoval(String sql) {
        fixtures.dataset(1);
        assertRejected(sql, "55000", "initialized dataset configuration must be retained");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dataset_control", Long.class)).isEqualTo(1);
    }

    @Test
    void rejectedCounterUpdateLeavesPriorValueIntact() {
        fixtures.dataset(10);
        assertRejected("UPDATE dataset_control SET last_commit_seq = 0", "23514", "must increase by exactly 1");
        assertThat(jdbc.queryForObject("SELECT last_commit_seq FROM dataset_control", Long.class)).isEqualTo(10);
    }
}
