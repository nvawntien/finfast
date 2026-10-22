package com.finfast.ledger.internal.domain;

import com.finfast.common.money.VndAmount;
import com.finfast.common.money.VndArithmetic;
import com.finfast.ledger.internal.domain.FinancialTransaction.Entry;
import com.finfast.ledger.internal.domain.FinancialTransaction.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTransactionTests {

    private static final UUID TRANSACTION = new UUID(0, 1);
    private static final UUID DEBIT_ENTRY = new UUID(0, 2);
    private static final UUID CREDIT_ENTRY = new UUID(0, 3);
    private static final UUID SOURCE = new UUID(0, 4);
    private static final UUID DESTINATION = new UUID(0, 5);
    private static final String REFERENCE = "  FT-EXACT-REFERENCE  ";
    private static final VndAmount AMOUNT = new VndAmount(200_000);
    private static final long POSITION = 42;
    private static final Instant RECORDED_AT = Instant.parse("2026-10-22T01:02:03.123456Z");

    @ParameterizedTest
    @EnumSource(value = Type.class, names = {"INTERNAL_TRANSFER", "DEMO_ALLOCATION"})
    void ordinaryTransactionsPreserveSuppliedFactsAndHaveExactOppositeDeltas(Type type) {
        List<Entry> entries = pair(AMOUNT.value());

        FinancialTransaction transaction = create(type, AMOUNT, POSITION, entries);

        assertMetadata(transaction, type, AMOUNT, POSITION);
        assertThat(transaction.entries()).containsExactlyElementsOf(entries);
        assertThat(transaction.entries().getFirst().id()).isEqualTo(DEBIT_ENTRY);
        assertThat(transaction.entries().getFirst().accountId()).isEqualTo(SOURCE);
        assertThat(transaction.entries().getFirst().deltaVnd()).isEqualTo(-200_000);
        assertThat(transaction.entries().getLast().id()).isEqualTo(CREDIT_ENTRY);
        assertThat(transaction.entries().getLast().accountId()).isEqualTo(DESTINATION);
        assertThat(transaction.entries().getLast().deltaVnd()).isEqualTo(200_000);
        assertThat(VndArithmetic.addExact(transaction.entries().getFirst().deltaVnd(),
                transaction.entries().getLast().deltaVnd())).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Type.class, names = {"INTERNAL_TRANSFER", "DEMO_ALLOCATION"})
    void ordinaryEntryOrderDoesNotChangeSignsOrRewriteTheSuppliedCollection(Type type) {
        List<Entry> creditFirst = List.of(pair(AMOUNT.value()).getLast(), pair(AMOUNT.value()).getFirst());

        assertThat(create(type, AMOUNT, POSITION, creditFirst).entries()).containsExactlyElementsOf(creditFirst);
    }

    @Test
    void genesisContainsOnlyThePositiveSupplyOnTheSuppliedTreasury() {
        Entry supply = new Entry(CREDIT_ENTRY, SOURCE, AMOUNT.value());

        FinancialTransaction transaction = create(Type.GENESIS, AMOUNT, POSITION, List.of(supply));

        assertMetadata(transaction, Type.GENESIS, AMOUNT, POSITION);
        assertThat(transaction.entries()).containsExactly(supply);
        assertThat(transaction.entries().getFirst().accountId()).isEqualTo(SOURCE);
        assertThat(transaction.entries().getFirst().deltaVnd()).isEqualTo(AMOUNT.value());
    }

    @ParameterizedTest
    @EnumSource(Type.class)
    void supportsMaximumPositiveAmountAndSuppliedPositionWithoutOverflow(Type type) {
        VndAmount maximum = new VndAmount(Long.MAX_VALUE);
        List<Entry> entries = type == Type.GENESIS
                ? List.of(new Entry(CREDIT_ENTRY, SOURCE, Long.MAX_VALUE)) : pair(Long.MAX_VALUE);

        FinancialTransaction transaction = create(type, maximum, Long.MAX_VALUE, entries);

        assertMetadata(transaction, type, maximum, Long.MAX_VALUE);
        assertThat(transaction.entries()).containsExactlyElementsOf(entries);
        if (type != Type.GENESIS) {
            assertThat(transaction.entries().getFirst().deltaVnd()).isEqualTo(-Long.MAX_VALUE);
            assertThat(VndArithmetic.addExact(transaction.entries().getFirst().deltaVnd(),
                    transaction.entries().getLast().deltaVnd())).isZero();
        }
    }

    @ParameterizedTest
    @EnumSource(Type.class)
    void rejectsZeroTransactionAmount(Type type) {
        List<Entry> entries = type == Type.GENESIS
                ? List.of(new Entry(CREDIT_ENTRY, SOURCE, 1)) : pair(1);

        assertThatIllegalArgumentException().isThrownBy(() -> create(type, new VndAmount(0), POSITION, entries));
    }

    @ParameterizedTest(name = "requires {0}")
    @MethodSource("nullRequiredValues")
    void rejectsNullRequiredValues(String field, Runnable construction) {
        assertThatNullPointerException().isThrownBy(construction::run).withMessage(field);
    }

    private static Stream<Arguments> nullRequiredValues() {
        return Stream.of(
                Arguments.of("id", (Runnable) () -> FinancialTransaction.internalTransfer(
                        null, REFERENCE, AMOUNT, POSITION, RECORDED_AT, pair(AMOUNT.value()))),
                Arguments.of("reference", (Runnable) () -> withReference(null)),
                Arguments.of("amount", (Runnable) () -> create(Type.INTERNAL_TRANSFER, null, POSITION, pair(AMOUNT.value()))),
                Arguments.of("recordedAt", (Runnable) () -> FinancialTransaction.internalTransfer(
                        TRANSACTION, REFERENCE, AMOUNT, POSITION, null, pair(AMOUNT.value()))),
                Arguments.of("entries", (Runnable) () -> create(Type.INTERNAL_TRANSFER, AMOUNT, POSITION, null)),
                Arguments.of("treasuryAccountId", (Runnable) () -> FinancialTransaction.genesis(
                        TRANSACTION, REFERENCE, AMOUNT, POSITION, RECORDED_AT, null,
                        List.of(new Entry(CREDIT_ENTRY, SOURCE, AMOUNT.value())))),
                Arguments.of("id", (Runnable) () -> new Entry(null, SOURCE, 1)),
                Arguments.of("accountId", (Runnable) () -> new Entry(DEBIT_ENTRY, null, -1)));
    }

    @Test
    void rejectsNullEntriesInsideTheCollection() {
        assertThatNullPointerException().isThrownBy(() -> create(Type.INTERNAL_TRANSFER, AMOUNT, POSITION,
                Arrays.asList(new Entry(DEBIT_ENTRY, SOURCE, -AMOUNT.value()), null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\r\n", "\u2003"})
    void rejectsBlankReferences(String reference) {
        assertThatIllegalArgumentException().isThrownBy(() -> withReference(reference));
    }

    @ParameterizedTest
    @ValueSource(strings = {"X", "😀"})
    void referenceBoundCountsCharactersAndPreservesExactText(String character) {
        String reference = character.repeat(64);

        assertThat(withReference(reference).reference()).isEqualTo(reference);
        assertThatIllegalArgumentException().isThrownBy(() -> withReference(character.repeat(65)));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonpositiveSuppliedPosition(long position) {
        assertThatIllegalArgumentException().isThrownBy(() -> create(Type.INTERNAL_TRANSFER, AMOUNT, position, pair(AMOUNT.value())));
    }

    @ParameterizedTest
    @EnumSource(value = Type.class, names = {"INTERNAL_TRANSFER", "DEMO_ALLOCATION"})
    void rejectsRepeatedParticipantAccounts(Type type) {
        List<Entry> entries = List.of(new Entry(DEBIT_ENTRY, SOURCE, -AMOUNT.value()),
                new Entry(CREDIT_ENTRY, SOURCE, AMOUNT.value()));

        assertThatIllegalArgumentException().isThrownBy(() -> create(type, AMOUNT, POSITION, entries));
    }

    @ParameterizedTest
    @EnumSource(value = Type.class, names = {"INTERNAL_TRANSFER", "DEMO_ALLOCATION"})
    void rejectsRepeatedEntryIdentitiesEvenWithDistinctAccounts(Type type) {
        List<Entry> entries = List.of(new Entry(DEBIT_ENTRY, SOURCE, -AMOUNT.value()),
                new Entry(DEBIT_ENTRY, DESTINATION, AMOUNT.value()));

        assertThatIllegalArgumentException().isThrownBy(() -> create(type, AMOUNT, POSITION, entries));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPairedDeltas")
    void rejectsWrongSignsMagnitudesAndOverflowProneDeltas(String scenario, long first, long second) {
        List<Entry> entries = List.of(new Entry(DEBIT_ENTRY, SOURCE, first), new Entry(CREDIT_ENTRY, DESTINATION, second));

        assertThatIllegalArgumentException().isThrownBy(() -> create(Type.INTERNAL_TRANSFER, AMOUNT, POSITION, entries));
    }

    private static Stream<Arguments> invalidPairedDeltas() {
        return Stream.of(
                Arguments.of("two credits", 200_000L, 200_000L),
                Arguments.of("two debits", -200_000L, -200_000L),
                Arguments.of("balanced but wrong amount", -199_999L, 199_999L),
                Arguments.of("unbalanced amounts", -200_000L, 199_999L),
                Arguments.of("minimum signed delta cannot match a supported amount", Long.MIN_VALUE, Long.MAX_VALUE),
                Arguments.of("sum would overflow if evaluated before validation", Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @ParameterizedTest
    @MethodSource("invalidCardinalities")
    void rejectsIncompleteOrExcessEntries(Type type, List<Entry> entries) {
        assertThatIllegalArgumentException().isThrownBy(() -> create(type, AMOUNT, POSITION, entries));
    }

    private static Stream<Arguments> invalidCardinalities() {
        Entry debit = pair(AMOUNT.value()).getFirst();
        Entry credit = pair(AMOUNT.value()).getLast();
        Entry extra = new Entry(new UUID(0, 6), new UUID(0, 7), 1);
        return Stream.concat(Stream.of(Type.INTERNAL_TRANSFER, Type.DEMO_ALLOCATION).flatMap(type -> Stream.of(
                Arguments.of(type, List.of()), Arguments.of(type, List.of(debit)),
                Arguments.of(type, List.of(debit, credit, extra)))),
                Stream.of(Arguments.of(Type.GENESIS, List.of()), Arguments.of(Type.GENESIS, List.of(debit, credit))));
    }

    @ParameterizedTest
    @ValueSource(longs = {-200_000, 199_999, Long.MIN_VALUE})
    void genesisRejectsNegativeOrMismatchedSupply(long delta) {
        assertThatIllegalArgumentException().isThrownBy(() -> create(Type.GENESIS, AMOUNT, POSITION,
                List.of(new Entry(CREDIT_ENTRY, SOURCE, delta))));
    }

    @Test
    void genesisRejectsAnEntryOnAnotherAccount() {
        assertThatIllegalArgumentException().isThrownBy(() -> create(Type.GENESIS, AMOUNT, POSITION,
                List.of(new Entry(CREDIT_ENTRY, DESTINATION, AMOUNT.value()))));
    }

    @Test
    void entryCannotRepresentAZeroDelta() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Entry(DEBIT_ENTRY, SOURCE, 0));
    }

    @Test
    void entriesAreDefensivelyCopiedAndCannotBeChangedThroughTheResult() {
        List<Entry> original = new ArrayList<>(pair(AMOUNT.value()));
        FinancialTransaction transaction = create(Type.INTERNAL_TRANSFER, AMOUNT, POSITION, original);

        original.set(0, new Entry(DEBIT_ENTRY, SOURCE, -1));
        original.clear();

        assertThat(transaction.entries()).containsExactlyElementsOf(pair(AMOUNT.value()));
        assertThatThrownBy(() -> transaction.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> transaction.entries().add(new Entry(new UUID(0, 8), SOURCE, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> transaction.entries().set(0, new Entry(DEBIT_ENTRY, SOURCE, -1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static List<Entry> pair(long amount) {
        return List.of(new Entry(DEBIT_ENTRY, SOURCE, VndArithmetic.negateExact(amount)),
                new Entry(CREDIT_ENTRY, DESTINATION, amount));
    }

    private static FinancialTransaction create(Type type, VndAmount amount, long position, List<Entry> entries) {
        return switch (type) {
            case INTERNAL_TRANSFER -> FinancialTransaction.internalTransfer(TRANSACTION, REFERENCE, amount, position, RECORDED_AT, entries);
            case DEMO_ALLOCATION -> FinancialTransaction.demoAllocation(TRANSACTION, REFERENCE, amount, position, RECORDED_AT, entries);
            case GENESIS -> FinancialTransaction.genesis(TRANSACTION, REFERENCE, amount, position, RECORDED_AT, SOURCE, entries);
        };
    }

    private static FinancialTransaction withReference(String reference) {
        return FinancialTransaction.internalTransfer(TRANSACTION, reference, AMOUNT, POSITION, RECORDED_AT, pair(AMOUNT.value()));
    }

    private static void assertMetadata(FinancialTransaction transaction, Type type, VndAmount amount, long position) {
        assertThat(transaction.id()).isEqualTo(TRANSACTION);
        assertThat(transaction.reference()).isEqualTo(REFERENCE);
        assertThat(transaction.type()).isEqualTo(type);
        assertThat(transaction.amount()).isEqualTo(amount);
        assertThat(transaction.commitSeq()).isEqualTo(position);
        assertThat(transaction.recordedAt()).isEqualTo(RECORDED_AT);
    }
}
