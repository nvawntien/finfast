package com.finfast.contracts;

import com.finfast.accounts.api.AccountIdentityQueries;
import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AdminAccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;
import com.finfast.ledger.api.AdminFinancialTransactionQueries.Entry;
import com.finfast.ledger.api.AdminFinancialTransactionQueries.Transaction;
import com.finfast.ledger.api.AdminFinancialTransactionQueries.Type;
import com.finfast.ledger.api.GenesisDatasetInspection;
import com.finfast.ledger.api.GenesisDatasetInspection.InitializedDataset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadCapabilityContractTests {
    private static final UUID ID = new UUID(0, 1);
    private static final UUID OWNER = new UUID(0, 2);
    private static final AccountNumber NUMBER = new AccountNumber("FF-EXAMPLE");
    private static final Entry CREDIT = new Entry(NUMBER, Long.MAX_VALUE);
    private static final Entry DEBIT = new Entry(new AccountNumber("FF-OTHER"), -Long.MAX_VALUE);

    @Test
    void newCapabilitiesAreAbstractWithoutFalseDefaultImplementations() {
        for (Class<?> capability : List.of(GenesisDatasetInspection.class, AccountIdentityQueries.class,
                AdminAccountQueries.class, AdminFinancialTransactionQueries.class)) {
            assertThat(capability.isInterface()).isTrue();
            assertThat(capability.getDeclaredMethods()).hasSize(1).allSatisfy(method -> {
                assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
                assertThat(method.isDefault()).isFalse();
            });
        }
        assertThat(GenesisDatasetInspection.class.getDeclaredMethods()[0].getReturnType()).isEqualTo(Optional.class);
        assertThat(AccountIdentityQueries.class.getDeclaredMethods()[0].getReturnType()).isEqualTo(Optional.class);
        assertThat(new AdminAccountQueries.Listing(List.of()).items()).isEmpty();
        assertThat(new AdminFinancialTransactionQueries.Transactions(List.of()).items()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("missingComponents")
    void rejectsNullReferenceComponents(Class<?> record, Object[] arguments) {
        assertThatThrownBy(() -> record.getDeclaredConstructors()[0].newInstance(arguments))
                .hasCauseInstanceOf(NullPointerException.class);
    }

    static Stream<Arguments> missingComponents() {
        return Stream.of(
                new Example(InitializedDataset.class, new Object[]{ID, OWNER, "REF", new VndAmount(1), Instant.EPOCH}),
                new Example(AccountIdentityQueries.Identity.class,
                        new Object[]{ID, NUMBER, AccountIdentityQueries.Kind.CUSTOMER, Optional.of(OWNER)}),
                new Example(AdminAccountQueries.Listing.class, new Object[]{List.of()}),
                new Example(AdminFinancialTransactionQueries.Transactions.class, new Object[]{List.of()}),
                new Example(Entry.class, new Object[]{NUMBER, 1L}),
                new Example(Transaction.class, new Object[]{ID, "REF", Type.GENESIS, new VndAmount(1), Instant.EPOCH,
                        List.of(new Entry(NUMBER, 1))}))
                .flatMap(example -> IntStream.range(0, example.arguments().length)
                        .filter(index -> !example.record().getRecordComponents()[index].getType().isPrimitive())
                        .mapToObj(index -> {
                            Object[] copy = example.arguments().clone();
                            copy[index] = null;
                            return Arguments.of(example.record(), copy);
                        }));
    }

    @Test
    void metadataExposesIdentityOnlyAndEnforcesCustomerVersusTreasuryOwnership() {
        var customer = new AccountIdentityQueries.Identity(ID, NUMBER, AccountIdentityQueries.Kind.CUSTOMER, Optional.of(OWNER));
        var treasury = new AccountIdentityQueries.Identity(ID, NUMBER, AccountIdentityQueries.Kind.TREASURY, Optional.empty());
        assertThat(customer.customerId()).contains(OWNER);
        assertThat(treasury.customerId()).isEmpty();
        assertThat(Arrays.stream(AccountIdentityQueries.Identity.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("accountId", "number", "kind", "customerId");
        assertThatThrownBy(() -> new AccountIdentityQueries.Identity(ID, NUMBER, AccountIdentityQueries.Kind.CUSTOMER, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountIdentityQueries.Identity(ID, NUMBER, AccountIdentityQueries.Kind.TREASURY, Optional.of(OWNER)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidReferences")
    void rejectsInvalidReferences(String reference) {
        assertThatThrownBy(() -> new InitializedDataset(ID, OWNER, reference, new VndAmount(1), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Transaction(ID, reference, Type.GENESIS, new VndAmount(1), Instant.EPOCH,
                List.of(new Entry(NUMBER, 1)))).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<String> invalidReferences() {
        return Stream.of("", " ", "bad\0ref", "a".repeat(65), "\uD800", "😀".repeat(65));
    }

    @Test
    void exactReferencesAndPositiveMaximumSupplyRemainSupported() {
        String reference = "😀".repeat(64);
        var dataset = new InitializedDataset(ID, OWNER, reference, new VndAmount(Long.MAX_VALUE), Instant.EPOCH);
        assertThat(dataset.genesisReference()).isEqualTo(reference);
        assertThat(new Transaction(ID, "  REF  ", Type.GENESIS, dataset.initialSupply(), Instant.EPOCH, List.of(CREDIT)).reference())
                .isEqualTo("  REF  ");
        assertThatThrownBy(() -> new InitializedDataset(ID, OWNER, "REF", new VndAmount(0), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Entry(NUMBER, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(Type.class)
    void financialProjectionPreservesTypeSignsAmountAndDefensiveCopies(Type type) {
        var entries = new ArrayList<>(type == Type.GENESIS ? List.of(CREDIT) : List.of(DEBIT, CREDIT));
        var transaction = new Transaction(ID, "REF", type, new VndAmount(Long.MAX_VALUE), Instant.EPOCH, entries);
        entries.clear();
        assertThat(transaction.type()).isEqualTo(type);
        assertThat(transaction.entries()).containsExactlyElementsOf(type == Type.GENESIS ? List.of(CREDIT) : List.of(DEBIT, CREDIT));
        assertThatThrownBy(() -> transaction.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
        var providerItems = new ArrayList<>(List.of(transaction));
        var listing = new AdminFinancialTransactionQueries.Transactions(providerItems);
        providerItems.clear();
        assertThat(listing.items()).containsExactly(transaction);
        assertThatThrownBy(() -> listing.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void accountListingCopiesCustomerSnapshotsWithoutCustomerDisplayOrCredentials() {
        var account = new CustomerAccount(ID, OWNER, NUMBER, CustomerAccount.Status.FROZEN, new VndAmount(0));
        var providerItems = new ArrayList<>(List.of(account));
        var listing = new AdminAccountQueries.Listing(providerItems);
        providerItems.clear();
        assertThat(listing.items()).containsExactly(account);
        assertThatThrownBy(() -> listing.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new AdminAccountQueries.Listing(Arrays.asList(account, null))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdminFinancialTransactionQueries.Transactions(Arrays.asList((Transaction) null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void accountListingHasDeterministicOrderWithoutMutatingInputOrDroppingRows() {
        var first = new CustomerAccount(new UUID(0, 3), OWNER, new AccountNumber("FF-001"),
                CustomerAccount.Status.ACTIVE, new VndAmount(10));
        var second = new CustomerAccount(new UUID(0, 4), new UUID(0, 5), new AccountNumber("FF-002"),
                CustomerAccount.Status.FROZEN, new VndAmount(20));
        var third = new CustomerAccount(new UUID(0, 6), new UUID(0, 7), new AccountNumber("FF-010"),
                CustomerAccount.Status.ACTIVE, new VndAmount(30));
        var input = new ArrayList<>(List.of(third, first, second));
        var listing = new AdminAccountQueries.Listing(input);
        assertThat(listing.items()).containsExactly(first, second, third);
        assertThat(new AdminAccountQueries.Listing(List.of(second, third, first))).isEqualTo(listing);
        assertThat(input).containsExactly(third, first, second);
        assertThatThrownBy(() -> listing.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidFinancialShapes")
    void rejectsIncompleteOrInconsistentFinancialProjection(Type type, long amount, List<Entry> entries) {
        assertThatThrownBy(() -> new Transaction(ID, "REF", type, new VndAmount(amount), Instant.EPOCH, entries))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidFinancialShapes() {
        return Stream.of(
                Arguments.of(Type.GENESIS, 1, List.of()),
                Arguments.of(Type.GENESIS, 1, List.of(new Entry(NUMBER, -1))),
                Arguments.of(Type.GENESIS, 1, List.of(new Entry(NUMBER, 2))),
                Arguments.of(Type.GENESIS, 0, List.of(new Entry(NUMBER, 1))),
                Arguments.of(Type.INTERNAL_TRANSFER, 1, List.of(new Entry(NUMBER, 1))),
                Arguments.of(Type.DEMO_ALLOCATION, 1, List.of(new Entry(NUMBER, 1), new Entry(NUMBER, -1))),
                Arguments.of(Type.INTERNAL_TRANSFER, 1, List.of(new Entry(NUMBER, 1), new Entry(new AccountNumber("OTHER"), -2))),
                Arguments.of(Type.DEMO_ALLOCATION, Long.MAX_VALUE, List.of(CREDIT, new Entry(new AccountNumber("OTHER"), Long.MIN_VALUE))));
    }

    private record Example(Class<?> record, Object[] arguments) { }
}
