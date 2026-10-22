package com.finfast;

import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.GenesisPosting;
import com.finfast.ledger.api.PostingResult;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcFinancialTransactionRepository;
import com.finfast.ledger.internal.application.usecase.GenesisPostingService;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.UnexpectedRollbackException;

import java.math.BigInteger;
import java.sql.Connection;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(45)
class GenesisPostingIntegrationTests extends LedgerGenesisTestSupport {

    @ParameterizedTest
    @ValueSource(longs = {1, 1_000_000, Long.MAX_VALUE})
    void initializesExactlyOneCompleteGenesis(long supply) {
        GenesisState before = genesisState();
        PostingResult.Pending pending = inTransaction(() -> {
            UUID treasury = createTreasury(); // Simulates provisioning within the caller-owned setup transaction.
            PostingResult.Pending result = genesis.initialize(treasury, new VndAmount(supply));
            assertThat(balance(treasury)).isEqualTo(supply);
            assertThat(committedGenesisState()).as("Pending is not a commit acknowledgement").isEqualTo(before);
            return result;
        });
        GenesisState committed = genesisState();
        assertThat(committed.treasuries()).isEqualTo(1);
        assertThat(committed.transactions()).isEqualTo(1);
        assertThat(committed.entries()).isEqualTo(1);
        assertThat(committed.datasets()).isEqualTo(1);
        assertThat(committed.balance()).isEqualTo(BigInteger.valueOf(supply));
        assertThat(committed.supply()).isEqualTo(supply);
        assertThat(committed.position()).isEqualTo(1);
        assertThat(committed.transactionId()).isEqualTo(pending.transactionId());
        assertThat(pending.amount()).isEqualTo(new VndAmount(supply));
        assertThat(pending.reference()).isEqualTo("GENESIS-" + pending.transactionId()).hasSize(44);
        assertThat(pending.recordedAt()).isEqualTo(TIME.truncatedTo(ChronoUnit.MICROS));
        FinancialTransaction persisted = inTransaction(() -> repository.findById(pending.transactionId()).orElseThrow());
        assertThat(persisted.id()).isEqualTo(pending.transactionId());
        assertThat(persisted.reference()).isEqualTo(pending.reference());
        assertThat(persisted.recordedAt()).isEqualTo(pending.recordedAt());
        assertThat(persisted.amount()).isEqualTo(pending.amount());
        assertThat(persisted.type()).isEqualTo(FinancialTransaction.Type.GENESIS);
        assertThat(persisted.commitSeq()).isEqualTo(1);
        assertThat(persisted.entries()).hasSize(1);
        assertThat(persisted.entries().getFirst().id()).isNotNull();
        assertThat(persisted.entries().getFirst().accountId()).isEqualTo(committed.treasuryId());
        assertThat(persisted.entries().getFirst().deltaVnd()).isEqualTo(supply);
        assertThat(accountsProbe.attempts.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "financial_transaction", "ledger_entry", "dataset_control", "pending"})
    void failureLeavesPreexistingTreasuryUnfunded(String stage) {
        verifyFailure(stage, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "financial_transaction", "ledger_entry", "dataset_control", "pending"})
    void failureAlsoRollsBackTreasuryProvisionedInsideOuterTransaction(String stage) {
        verifyFailure(stage, true);
    }

    private void verifyFailure(String stage, boolean provisionInside) {
        UUID preexisting = provisionInside ? null : inTransaction(this::createTreasury);
        GenesisState before = genesisState();
        if (stage.equals("accounts")) {
            accountsProbe.afterInitialization = () -> {
                assertThat(committedGenesisState()).isEqualTo(before);
                throw new IllegalStateException("injected after Accounts initialization");
            };
        } else if (!stage.equals("pending")) {
            failAfterInsert(stage);
        }
        RuntimeException error = assertThrows(RuntimeException.class, () -> inTransaction(() -> {
            UUID treasury = provisionInside ? createTreasury() : preexisting;
            PostingResult.Pending result = genesis.initialize(treasury, new VndAmount(100));
            assertThat(result).isNotNull();
            assertThat(committedGenesisState()).isEqualTo(before);
            throw new IllegalStateException("injected after Pending");
        }));
        if (stage.equals("accounts") || stage.equals("pending")) {
            assertThat(error).isInstanceOf(IllegalStateException.class).hasMessageContaining("injected after");
        } else {
            assertThat(error).isInstanceOfSatisfying(DataAccessException.class,
                    failure -> LedgerPersistenceIntegrationTests.assertSqlState(failure, "P0001"));
        }
        assertThat(genesisState()).as("verified after the outer transaction finished").isEqualTo(before);
        if (preexisting != null) {
            assertThat(balance(preexisting)).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"same", "differentSupply", "differentAccount", "recreatedContext"})
    void repeatedInitializationNeverReseeds(String variant) {
        UUID treasury = inTransaction(this::createTreasury);
        PostingResult.Pending original = inTransaction(() -> genesis.initialize(treasury, new VndAmount(100)));
        GenesisState before = genesisState();
        var entries = inTransaction(() -> repository.findById(original.transactionId()).orElseThrow().entries());
        if (variant.equals("recreatedContext")) {
            recreateContext(); // Same database, new Spring objects; not a process/deployment restart test.
        }
        int attemptsBefore = accountsProbe.attempts.get();
        assertThatThrownBy(() -> inTransaction(() -> genesis.initialize(
                variant.equals("differentAccount") ? source : treasury,
                new VndAmount(variant.equals("differentSupply") ? 200 : 100))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already initialized");
        assertThat(accountsProbe.attempts.get()).isEqualTo(attemptsBefore);
        assertThat(genesisState()).isEqualTo(before);
        assertThat(inTransaction(() -> repository.findById(original.transactionId()).orElseThrow().entries())).isEqualTo(entries);
    }

    @Test
    void requiresActualMandatoryProxiesAndExistingTransaction() {
        assertThat(AopUtils.isAopProxy(genesis)).isTrue();
        assertThat(AopUtils.isAopProxy(dataset)).isTrue();
        assertThat(AopUtils.isAopProxy(accounts)).isTrue();
        UUID treasury = inTransaction(this::createTreasury);
        GenesisState before = genesisState();
        assertThatThrownBy(() -> genesis.initialize(treasury, new VndAmount(100)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(dataset::requireUninitialized).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(accountsProbe.attempts.get()).isZero();
        assertThat(genesisState()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(ints = {Connection.TRANSACTION_REPEATABLE_READ, Connection.TRANSACTION_SERIALIZABLE})
    void rejectsIncorrectIsolationBeforeCallingAccounts(int isolation) {
        UUID treasury = inTransaction(this::createTreasury);
        GenesisState before = genesisState();
        var outer = transaction();
        outer.setIsolationLevel(isolation);
        assertThatThrownBy(() -> outer.execute(status -> genesis.initialize(treasury, new VndAmount(100))))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(accountsProbe.attempts.get()).isZero();
        assertThat(genesisState()).isEqualTo(before);
    }

    @Test
    void rejectsReadOnlyBeforeCallingAccounts() {
        UUID treasury = inTransaction(this::createTreasury);
        GenesisState before = genesisState();
        var outer = transaction();
        outer.setReadOnly(true);
        assertThatThrownBy(() -> outer.execute(status -> genesis.initialize(treasury, new VndAmount(100))))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(accountsProbe.attempts.get()).isZero();
        assertThat(genesisState()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nullTreasury", "nullSupply", "zeroSupply"})
    void validatesArgumentsBeforeCallingAccounts(String invalid) {
        GenesisState before = genesisState();
        assertThatThrownBy(() -> inTransaction(() -> genesis.initialize(
                invalid.equals("nullTreasury") ? null : source,
                invalid.equals("nullSupply") ? null : new VndAmount(invalid.equals("zeroSupply") ? 0 : 100))))
                .isInstanceOf(invalid.equals("zeroSupply") ? IllegalArgumentException.class : NullPointerException.class);
        assertThat(accountsProbe.attempts.get()).isZero();
        assertThat(genesisState()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.5", "9223372036854775808"})
    void unrepresentableSupplyCannotEnterTheTypedContract(String invalid) {
        GenesisState before = genesisState();
        assertThatThrownBy(() -> genesis.initialize(source, VndAmount.parse(invalid)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(accountsProbe.attempts.get()).isZero();
        assertThat(genesisState()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "customer", "funded"})
    void delegatesTreasuryEligibilityToAccounts(String invalid) {
        UUID treasury = switch (invalid) {
            case "missing" -> UUID.randomUUID();
            case "customer" -> source;
            default -> inTransaction(this::createTreasury);
        };
        if (invalid.equals("funded")) {
            ledgerJdbc.update("UPDATE public.account SET balance_vnd = 7 WHERE id = ?", treasury); // Negative fixture only.
        }
        GenesisState before = genesisState();
        assertThatThrownBy(() -> inTransaction(() -> genesis.initialize(treasury, new VndAmount(100))))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("Test Accounts");
        assertThat(accountsProbe.attempts.get()).isEqualTo(1);
        assertThat(genesisState()).isEqualTo(before);
    }

    @Test
    void existingOrphanGenesisIsNotReplacedOrRepaired() {
        UUID treasury = inTransaction(this::createTreasury);
        fixtures.transaction("type", "GENESIS", "commit_seq", 9L); // Deliberately incomplete preexisting state.
        GenesisState before = genesisState();
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> inTransaction(() -> genesis.initialize(treasury, new VndAmount(100))));
        LedgerPersistenceIntegrationTests.assertSqlState(failure, "23505");
        assertThat(failure.getMostSpecificCause().getMessage()).contains("uq_single_genesis");
        assertThat(genesisState()).isEqualTo(before);
        assertThat(balance(treasury)).isZero();
    }

    @Test
    void duplicateGeneratedReferenceRollsBackTreasuryAndAllFacts() {
        UUID treasury = inTransaction(this::createTreasury);
        GenesisState before = genesisState();
        // Test-only collision injector delegates the actual write to the unchanged Phase 2 implementation.
        ledgerContext.registerBean("collisionFacts", JdbcFinancialTransactionRepository.class, () ->
                new JdbcFinancialTransactionRepository(ledgerJdbc) {
                    @Override
                    public void insert(FinancialTransaction fact) {
                        fixtures.transaction("reference", fact.reference(), "commit_seq", 2L);
                        super.insert(fact);
                    }
                });
        ledgerContext.registerBean("collisionGenesis", GenesisPostingService.class, () -> new GenesisPostingService(accounts,
                ledgerContext.getBean("collisionFacts", JdbcFinancialTransactionRepository.class), dataset,
                ledgerContext.getBean(Clock.class)));
        GenesisPosting collision = ledgerContext.getBean("collisionGenesis", GenesisPosting.class);
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> inTransaction(() -> collision.initialize(treasury, new VndAmount(100))));
        LedgerPersistenceIntegrationTests.assertSqlState(failure, "23505");
        assertThat(failure.getMostSpecificCause().getMessage()).contains("uq_financial_transaction_reference");
        assertThat(genesisState()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"customerParticipant", "missingTransaction"})
    void brokenDatasetLinkageFailsAndRollsBack(String invalid) {
        UUID treasury = invalid.equals("customerParticipant") ? source : inTransaction(this::createTreasury);
        GenesisState before = genesisState();
        DataAccessException failure = assertThrows(DataAccessException.class, () -> inTransaction(() -> {
            FinancialTransaction fact = FinancialTransaction.genesis(UUID.randomUUID(), "INVALID-LINK", new VndAmount(100), 1,
                    TIME, treasury, List.of(new FinancialTransaction.Entry(UUID.randomUUID(), treasury, 100)));
            if (invalid.equals("customerParticipant")) {
                repository.insert(fact);
            }
            dataset.insertGenesis(fact);
            return null;
        }));
        LedgerPersistenceIntegrationTests.assertSqlState(failure, "23514");
        assertThat(failure.getMostSpecificCause().getMessage()).contains(invalid.equals("customerParticipant")
                ? "must reference a TREASURY account" : "must reference a GENESIS transaction");
        assertThat(genesisState()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ordinary", "noninitialPosition"})
    void datasetWriterRejectsFactsOutsideInitialGenesis(String invalid) {
        FinancialTransaction value = invalid.equals("ordinary") ? fact(FinancialTransaction.Type.INTERNAL_TRANSFER, 1)
                : FinancialTransaction.genesis(UUID.randomUUID(), "WRONG-POSITION", new VndAmount(100), 2, TIME,
                    source, List.of(new FinancialTransaction.Entry(UUID.randomUUID(), source, 100)));
        GenesisState before = genesisState();
        assertThatThrownBy(() -> inTransaction(() -> {
            dataset.insertGenesis(value);
            return null;
        })).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("posting position 1");
        assertThat(genesisState()).isEqualTo(before);
    }

    @Test
    void caughtGenesisFailureStillPreventsOuterCommit() {
        UUID treasury = inTransaction(this::createTreasury);
        GenesisState before = genesisState();
        failAfterInsert("dataset_control");
        assertThatThrownBy(() -> inTransaction(() -> {
            assertThrows(DataAccessException.class, () -> genesis.initialize(treasury, new VndAmount(100)));
            return null;
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(genesisState()).isEqualTo(before);
    }
}
