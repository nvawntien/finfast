package com.finfast;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.common.error.ErrorCode;
import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.AllocationPosting;
import com.finfast.ledger.api.FinancialPosting;
import com.finfast.ledger.api.PostingResult;
import com.finfast.ledger.internal.application.usecase.LedgerPostingError;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.UnexpectedRollbackException;

import java.sql.Connection;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.finfast.OrdinaryPostingTestAccounts.FixtureRejection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(45)
class OrdinaryPostingIntegrationTests extends LedgerOrdinaryPostingTestSupport {

    static Stream<Arguments> successfulAmounts() {
        return Stream.of(Operation.values()).flatMap(operation -> Stream.of(1L, 75L, Long.MAX_VALUE)
                .map(amount -> Arguments.of(operation, amount)));
    }

    @ParameterizedTest
    @MethodSource("successfulAmounts")
    void persistsOneCompleteMovementWithTentativeBalances(Operation operation, long amount) {
        prepare(operation, amount, amount);
        String before = financialState();
        long previousPosition = position();
        long previousTransactions = snapshot().transactions();
        long previousEntries = snapshot().entries();
        PostingResult.Pending pending = inTransaction(() -> {
            PostingResult result = post(operation, amount);
            assertThat(result).isInstanceOf(PostingResult.Pending.class);
            assertThat(committedFinancialState()).as("method return has not committed any effect").isEqualTo(before);
            return (PostingResult.Pending) result;
        });
        UUID debit = operation == Operation.TRANSFER ? source : treasury;
        assertThat(balance(debit)).isZero();
        assertThat(balance(destination)).isEqualTo(amount);
        assertThat(position()).isEqualTo(previousPosition + 1);
        assertThat(snapshot().transactions()).isEqualTo(previousTransactions + 1);
        assertThat(snapshot().entries()).isEqualTo(previousEntries + 2);
        FinancialTransaction stored = inTransaction(() -> repository.findById(pending.transactionId()).orElseThrow());
        assertThat(stored.type()).isEqualTo(operation == Operation.TRANSFER
                ? FinancialTransaction.Type.INTERNAL_TRANSFER : FinancialTransaction.Type.DEMO_ALLOCATION);
        assertThat(stored.id()).isEqualTo(pending.transactionId());
        assertThat(stored.reference()).isEqualTo((operation == Operation.TRANSFER ? "TRANSFER-" : "ALLOCATION-") + stored.id());
        assertThat(pending.reference()).isEqualTo(stored.reference());
        assertThat(stored.recordedAt()).isEqualTo(TIME.truncatedTo(ChronoUnit.MICROS)).isEqualTo(pending.recordedAt());
        assertThat(stored.amount()).isEqualTo(new VndAmount(amount)).isEqualTo(pending.amount());
        assertThat(stored.commitSeq()).isEqualTo(previousPosition + 1);
        assertThat(stored.entries()).hasSize(2);
        assertThat(stored.entries().getFirst().accountId()).isEqualTo(debit);
        assertThat(stored.entries().getFirst().deltaVnd()).isEqualTo(-amount);
        assertThat(stored.entries().getLast().accountId()).isEqualTo(destination);
        assertThat(stored.entries().getLast().deltaVnd()).isEqualTo(amount);
        assertThat(stored.entries().getFirst().id()).isNotEqualTo(stored.entries().getLast().id());
        assertThat(accountsProbe.attempts.get()).isEqualTo(1); // Setup only; ordinary posting never initializes Treasury.
        if (operation == Operation.TRANSFER) {
            assertThat(pairProbe.lastTransfer).isEqualTo(new OrdinaryPostingTestAccounts.TransferArguments(
                    source, owner(source), number(destination), new VndAmount(amount)));
            assertThat(pairProbe.allocations.get()).isZero();
        } else {
            assertThat(pairProbe.transfers.get()).isZero();
        }
        assertFinancialInvariants();
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void referencesAreUniqueAndTextIntentIsPreserved(Operation operation) {
        prepare(operation, 200, 100);
        FinancialPosting.Intent transfer = transfer(source, destination, 40);
        AllocationPosting.Intent allocation = allocation(destination, 40);
        PostingResult.Pending first = inTransaction(() -> (PostingResult.Pending) (operation == Operation.TRANSFER
                ? transfers.postTransfer(transfer) : allocations.postAllocation(allocation)));
        PostingResult.Pending second = inTransaction(() -> (PostingResult.Pending) post(operation, 40));
        assertThat(second.transactionId()).isNotEqualTo(first.transactionId());
        assertThat(second.reference()).isNotEqualTo(first.reference());
        assertThat(transfer.note()).contains("  exact transfer note 😀  ");
        assertThat(allocation.reason()).isEqualTo("  exact demo reason 😀  ");
        assertThat(ledgerJdbc.queryForObject("SELECT count(*) FROM public.admin_audit_event", Long.class)).isZero();
        assertThat(ledgerJdbc.queryForObject("SELECT count(*) FROM public.request_outcome", Long.class)).isZero();
        assertFinancialInvariants();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void absentAndEmptyNotesStayDistinct(boolean present) {
        prepare(Operation.TRANSFER, 100, 50);
        Optional<String> note = present ? Optional.of("") : Optional.empty();
        var intent = new FinancialPosting.Intent(INITIATOR, owner(source), source, number(destination), new VndAmount(1), note);
        assertThat(inTransaction(() -> transfers.postTransfer(intent))).isInstanceOf(PostingResult.Pending.class);
        assertThat(intent.note()).isEqualTo(note);
        assertFinancialInvariants();
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void zeroAmountRejectsBeforeAccountsAndPositionReservation(Operation operation) {
        prepare(operation, 100, 50);
        String before = financialState();
        assertRejected(inTransaction(() -> post(operation, 0)), LedgerPostingError.INVALID_AMOUNT);
        assertThat(pairProbe.transfers.get()).isZero();
        assertThat(pairProbe.allocations.get()).isZero();
        assertThat(financialState()).isEqualTo(before);
        assertThat(LedgerPostingError.INVALID_AMOUNT.code()).isEqualTo("INVALID_AMOUNT");
        assertThat(LedgerPostingError.INVALID_AMOUNT.publicMessage()).isEqualTo("Amount must be positive");
        assertThat(LedgerPostingError.INVALID_AMOUNT.category()).isEqualTo(ErrorCode.Category.UNPROCESSABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void blankReasonRejectsBeforeAccountsAndPositionReservation(String reason) {
        initialize(100);
        String before = financialState();
        var intent = new AllocationPosting.Intent(ADMIN, number(destination), new VndAmount(10), reason);
        assertRejected(inTransaction(() -> allocations.postAllocation(intent)), LedgerPostingError.REASON_REQUIRED);
        assertThat(intent.reason()).isEqualTo(reason);
        assertThat(pairProbe.allocations.get()).isZero();
        assertThat(financialState()).isEqualTo(before);
        assertThat(LedgerPostingError.REASON_REQUIRED.code()).isEqualTo("REASON_REQUIRED");
        assertThat(LedgerPostingError.REASON_REQUIRED.publicMessage()).isEqualTo("Allocation reason is required");
        assertThat(LedgerPostingError.REASON_REQUIRED.category()).isEqualTo(ErrorCode.Category.UNPROCESSABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"insufficient", "frozenSource", "frozenRecipient", "missingRecipient", "missingSource",
            "sameAccount", "ownership", "treasurySource", "treasuryRecipient"})
    void accountsTransferRejectionPassesThroughWithoutAnyFinancialEffect(String invalid) {
        prepare(Operation.TRANSFER, 1000, 100);
        UUID debit = invalid.equals("missingSource") ? UUID.randomUUID() : invalid.equals("treasurySource") ? treasury : source;
        UUID credit = invalid.equals("sameAccount") ? source : invalid.equals("treasuryRecipient") ? treasury : destination;
        if (invalid.startsWith("frozen")) {
            ledgerJdbc.update("UPDATE public.account SET status = 'FROZEN' WHERE id = ?",
                    invalid.equals("frozenSource") ? source : destination);
        }
        var intent = new FinancialPosting.Intent(INITIATOR, invalid.equals("ownership") || invalid.equals("treasurySource")
                ? INITIATOR : owner(source), debit, invalid.equals("missingRecipient") ? new AccountNumber("MISSING") : number(credit),
                new VndAmount(invalid.equals("insufficient") ? 101 : 10), Optional.empty());
        FixtureRejection expected = switch (invalid) {
            case "insufficient" -> FixtureRejection.INSUFFICIENT_FUNDS;
            case "frozenSource", "frozenRecipient" -> FixtureRejection.FROZEN;
            case "missingSource", "missingRecipient" -> FixtureRejection.NOT_FOUND;
            case "sameAccount" -> FixtureRejection.SAME_ACCOUNT;
            case "ownership" -> FixtureRejection.OWNERSHIP_MISMATCH;
            default -> FixtureRejection.INVALID_TYPE;
        };
        String before = financialState();
        assertRejected(inTransaction(() -> transfers.postTransfer(intent)), expected);
        assertThat(financialState()).isEqualTo(before);
        assertFinancialInvariants();
    }

    @ParameterizedTest
    @ValueSource(strings = {"insufficient", "frozen", "missing", "treasuryRecipient"})
    void accountsAllocationRejectionPassesThroughWithoutAnyFinancialEffect(String invalid) {
        initialize(100);
        if (invalid.equals("frozen")) {
            ledgerJdbc.update("UPDATE public.account SET status = 'FROZEN' WHERE id = ?", destination);
        }
        var intent = new AllocationPosting.Intent(ADMIN, invalid.equals("missing") ? new AccountNumber("MISSING")
                : number(invalid.equals("treasuryRecipient") ? treasury : destination),
                new VndAmount(invalid.equals("insufficient") ? 101 : 10), "exact reason");
        FixtureRejection expected = switch (invalid) {
            case "insufficient" -> FixtureRejection.INSUFFICIENT_FUNDS;
            case "frozen" -> FixtureRejection.FROZEN;
            case "missing" -> FixtureRejection.NOT_FOUND;
            default -> FixtureRejection.INVALID_TYPE;
        };
        String before = financialState();
        assertRejected(inTransaction(() -> allocations.postAllocation(intent)), expected);
        assertThat(financialState()).isEqualTo(before);
        assertFinancialInvariants();
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void accountsDetectsResultOverflowBeforeWritingEitherBalance(Operation operation) {
        prepare(operation, 1000, 100);
        // Deliberately inconsistent isolated numeric fixture: overflow cannot occur in a valid conserved BIGINT dataset.
        ledgerJdbc.update("UPDATE public.account SET balance_vnd = ? WHERE id = ?", Long.MAX_VALUE, destination);
        String before = financialState();
        assertRejected(inTransaction(() -> post(operation, 1)), FixtureRejection.BALANCE_OVERFLOW);
        assertThat(financialState()).isEqualTo(before);
    }

    @Test
    void mandatorySpringBoundariesRejectStandaloneCalls() {
        prepare(Operation.TRANSFER, 100, 50);
        assertThat(AopUtils.isAopProxy(transfers)).isTrue();
        assertThat(AopUtils.isAopProxy(allocations)).isTrue();
        assertThat(AopUtils.isAopProxy(accounts)).isTrue();
        String before = financialState();
        assertThatThrownBy(() -> post(Operation.TRANSFER, 1)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> post(Operation.ALLOCATION, 1)).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(pairProbe.transfers.get()).isZero();
        assertThat(pairProbe.allocations.get()).isZero();
        assertThat(financialState()).isEqualTo(before);
    }

    static Stream<Arguments> wrongIsolation() {
        return Stream.of(Operation.values()).flatMap(operation -> Stream.of(Connection.TRANSACTION_REPEATABLE_READ,
                Connection.TRANSACTION_SERIALIZABLE).map(isolation -> Arguments.of(operation, isolation)));
    }

    @ParameterizedTest
    @MethodSource("wrongIsolation")
    void incompatibleIsolationRollsBackPendingAccountsChanges(Operation operation, int isolation) {
        prepare(operation, 1000, 100);
        String before = financialState();
        var outer = transaction();
        outer.setIsolationLevel(isolation);
        assertThatThrownBy(() -> outer.execute(status -> post(operation, 1)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(financialState()).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void readOnlyTransactionCannotWriteOrReturnPending(Operation operation) {
        prepare(operation, 1000, 100);
        String before = financialState();
        var outer = transaction();
        outer.setReadOnly(true);
        DataAccessException error = assertThrows(DataAccessException.class, () -> outer.execute(status -> post(operation, 1)));
        LedgerPersistenceIntegrationTests.assertSqlState(error, "25006");
        assertThat(financialState()).isEqualTo(before);
    }

    static Stream<Arguments> failurePoints() {
        return Stream.of(Operation.values()).flatMap(operation -> Stream.of("firstBalance", "secondBalance", "position",
                "transaction", "firstEntry", "secondEntry", "pending").map(stage -> Arguments.of(operation, stage)));
    }

    @ParameterizedTest
    @MethodSource("failurePoints")
    void technicalFailureRollsBackBalancesCounterAndAllFacts(Operation operation, String stage) {
        prepare(operation, 1000, 100);
        String before = financialState();
        long positionBefore = position();
        if (stage.endsWith("Balance")) {
            int failAt = stage.equals("firstBalance") ? 1 : 2;
            pairProbe.afterBalanceWrite = written -> {
                if (written == failAt) {
                    ledgerJdbc.execute("SELECT 1 / 0"); // Actual PostgreSQL failure after the selected balance write.
                }
            };
        } else if (!stage.equals("pending")) {
            installFinancialFailure(stage);
        }
        RuntimeException failure = assertThrows(RuntimeException.class, () -> inTransaction(() -> {
            PostingResult result = post(operation, 10);
            assertThat(result).isInstanceOf(PostingResult.Pending.class);
            assertThat(committedFinancialState()).isEqualTo(before);
            throw new IllegalStateException("injected after Pending");
        }));
        if (stage.equals("pending")) {
            assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("injected after Pending");
        } else {
            assertThat(failure).isInstanceOfSatisfying(DataAccessException.class, error ->
                    LedgerPersistenceIntegrationTests.assertSqlState(error, stage.endsWith("Balance") ? "22012" : "P0001"));
        }
        assertThat(financialState()).as("observe only after outer rollback has finished").isEqualTo(before);
        assertThat(position()).isEqualTo(positionBefore);
        assertFinancialInvariants();
    }

    private void installFinancialFailure(String stage) {
        String table = stage.equals("position") ? "dataset_control" : stage.equals("transaction") ? "financial_transaction" : "ledger_entry";
        String operation = stage.equals("position") ? "UPDATE" : "INSERT";
        String when = stage.equals("firstEntry") ? " WHEN (NEW.delta_vnd < 0)" : stage.equals("secondEntry") ? " WHEN (NEW.delta_vnd > 0)" : "";
        ledgerJdbc.execute("""
                CREATE FUNCTION public.test_fail_posting() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected ordinary posting failure' USING ERRCODE = 'P0001'; END; $$
                """);
        ledgerJdbc.execute("CREATE TRIGGER test_fail_posting AFTER " + operation + " ON public." + table
                + " FOR EACH ROW" + when + " EXECUTE FUNCTION public.test_fail_posting()");
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void caughtInnerTechnicalFailureStillForcesOuterRollback(Operation operation) {
        prepare(operation, 1000, 100);
        String before = financialState();
        installFinancialFailure("secondEntry");
        assertThatThrownBy(() -> inTransaction(() -> {
            assertThrows(DataAccessException.class, () -> post(operation, 10));
            return null;
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(financialState()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrExhaustedDatasetIsTechnicalAndRollsBackAccounts(boolean exhausted) {
        if (exhausted) {
            seedDataset(Long.MAX_VALUE); // Synthetic counter-boundary fixture, not the production GENESIS position.
            treasury = ledgerJdbc.queryForObject("SELECT treasury_account_id FROM public.dataset_control", UUID.class);
        } else {
            treasury = inTransaction(this::createTreasury);
            ledgerJdbc.update("UPDATE public.account SET balance_vnd = 100 WHERE id = ?", treasury); // Incomplete dataset fixture.
        }
        String before = financialState();
        assertThatThrownBy(() -> inTransaction(() -> allocations.postAllocation(allocation(destination, 10))))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("singleton missing or counter exhausted");
        assertThat(financialState()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingEntry", "wrongAccount", "wrongDelta", "wrongSupply", "extraEntry"})
    void incompleteGenesisCannotPost(String corruption) {
        treasury = inTransaction(this::createTreasury);
        ledgerJdbc.update("UPDATE public.account SET balance_vnd = 100 WHERE id = ?", treasury);
        UUID initial = fixtures.transaction("type", "GENESIS", "commit_seq", 1L);
        if (!corruption.equals("missingEntry")) {
            fixtures.entry(initial, corruption.equals("wrongAccount") ? source : treasury,
                    corruption.equals("wrongDelta") ? 99 : 100);
        }
        if (corruption.equals("extraEntry")) {
            fixtures.entry(initial, destination, 1);
        }
        fixtures.dataset(new DatabaseFixtures.DatasetReferences(treasury, initial),
                "initial_supply_vnd", corruption.equals("wrongSupply") ? 101L : 100L);
        String before = financialState();
        assertThatThrownBy(() -> inTransaction(() -> allocations.postAllocation(allocation(destination, 10))))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Incomplete GENESIS");
        assertThat(financialState()).isEqualTo(before); // Preserve corrupt input evidence; never repair or mint.
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void nullIntentIsATechnicalCallerErrorWithoutWrites(Operation operation) {
        initialize(100);
        String before = financialState();
        assertThatThrownBy(() -> inTransaction(() -> operation == Operation.TRANSFER
                ? transfers.postTransfer(null) : allocations.postAllocation(null)))
                .isInstanceOf(NullPointerException.class).hasMessage("intent");
        assertThat(pairProbe.transfers.get()).isZero();
        assertThat(pairProbe.allocations.get()).isZero();
        assertThat(financialState()).isEqualTo(before);
    }

    @Test
    void unsignedParticipantLockOrderMatchesPostgresAcrossSignedBoundaries() {
        initialize(1000);
        List<UUID> ids = List.of(new UUID(0, Long.MIN_VALUE), new UUID(0, Long.MAX_VALUE),
                new UUID(Long.MIN_VALUE, 0), new UUID(Long.MAX_VALUE, 0));
        for (UUID id : ids) {
            fixtures.account("id", id, "account_number", id.toString().replace("-", ""));
            fund(id, 100);
        }
        List<UUID> expected = ledgerJdbc.queryForList("SELECT id FROM public.account WHERE id IN (?, ?, ?, ?) ORDER BY id",
                UUID.class, ids.toArray());
        assertThat(ids.stream().sorted(OrdinaryPostingTestAccounts::compareUnsigned).toList()).isEqualTo(expected);
        for (int index = 1; index < expected.size(); index++) {
            resetPairProbe();
            UUID higher = expected.get(index);
            UUID lower = expected.get(index - 1);
            assertThat(inTransaction(() -> transfers.postTransfer(transfer(higher, lower, 1))))
                    .isInstanceOf(PostingResult.Pending.class);
            assertThat(pairProbe.lockedPairs).containsExactly(List.of(lower, higher));
        }
        assertFinancialInvariants();
    }

    private static void assertRejected(PostingResult result, ErrorCode expected) {
        assertThat(result).isInstanceOfSatisfying(PostingResult.Rejected.class,
                rejected -> assertThat(rejected.reason()).isSameAs(expected));
    }
}
