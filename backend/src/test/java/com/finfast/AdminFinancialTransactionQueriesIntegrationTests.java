package com.finfast;

import com.finfast.accounts.api.AccountIdentityQueries.Identity;
import com.finfast.accounts.api.AccountIdentityQueries.Kind;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(45)
class AdminFinancialTransactionQueriesIntegrationTests extends AdminFinancialQueriesTestSupport {
    @Test
    void emptyFinancialDatasetExcludesRejectionsAndAdministrativeEvents() {
        inTransaction(() -> {
            fixtures.outcome();
            fixtures.audit();
            return null;
        });
        var result = queries.listCommitted();
        assertThat(result.items()).isEmpty();
        assertThat(identities.calls).isEmpty();
        assertThatThrownBy(() -> result.items().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(queries.listCommitted()).isEqualTo(result);
        assertThat(ledgerJdbc.queryForObject("SELECT count(*) FROM request_outcome", Long.class)).isEqualTo(1);
        assertThat(ledgerJdbc.queryForObject("SELECT count(*) FROM admin_audit_event", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(Type.class)
    void allTypesPreserveExactFactsAndMaximumIntegralVnd(Type type) {
        var genesis = genesis(Long.MAX_VALUE, type == Type.GENESIS ? "😀".repeat(64) : " original GENESIS ", TIME);
        var fact = type == Type.GENESIS ? genesis : append(type, Long.MAX_VALUE, " 😀e\u0301 original ref  ", TIME,
                type == Type.DEMO_ALLOCATION ? genesis.entries().getFirst().accountId() : source, destination);
        var result = queries.listCommitted();
        var projection = result.items().getFirst();
        assertThat(projection.transactionId()).isEqualTo(fact.id());
        assertThat(projection.reference()).isEqualTo(fact.reference());
        assertThat(projection.type().name()).isEqualTo(type.name());
        assertThat(projection.amount().value()).isEqualTo(Long.MAX_VALUE);
        assertThat(projection.recordedAt()).isEqualTo(TIME.truncatedTo(ChronoUnit.MICROS));
        var expected = fact.entries().stream().sorted(java.util.Comparator.comparingLong(e -> e.deltaVnd()))
                .map(e -> new AdminFinancialTransactionQueries.Entry(number(e.accountId()), e.deltaVnd())).toList();
        assertThat(projection.entries()).containsExactlyElementsOf(expected);
        assertThatThrownBy(() -> projection.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(identities.calls.values()).allMatch(count -> count == 1);
        assertThat(queries.listCommitted()).isEqualTo(result);
        assertThat(identities.calls.values()).allMatch(count -> count == 2); // Cache belongs to one invocation only.
    }

    @Test
    void mixedFactsFollowPostingPositionsRegardlessOfTimestampsAndReuseAccountIdentities() {
        var genesis = genesis(100, "GENESIS exact", TIME.plusSeconds(500));
        var allocation = append(Type.DEMO_ALLOCATION, 100, "allocation exact", TIME,
                genesis.entries().getFirst().accountId(), source);
        var transfer = append(Type.INTERNAL_TRANSFER, 70, "transfer exact", TIME.minusSeconds(500), source, destination);
        inTransaction(() -> {
            fixtures.outcome("final_kind", "COMPLETED", "transaction_id", transfer.id(), "response_snapshot", "{}");
            fixtures.audit("action", "DEMO_ALLOCATION", "transaction_id", allocation.id(),
                    "target_account_id", source, "before_status", null, "after_status", null);
            return null;
        });
        var result = queries.listCommitted();
        assertThat(result.items()).extracting(AdminFinancialTransactionQueries.Transaction::transactionId)
                .containsExactly(transfer.id(), allocation.id(), genesis.id());
        assertThat(result.items()).extracting(t -> t.entries().stream().map(e -> e.deltaVnd()).toList())
                .containsExactly(List.of(-70L, 70L), List.of(-100L, 100L), List.of(100L));
        assertThat(identities.calls).hasSize(3);
        assertThat(identities.calls.values()).allMatch(count -> count == 1);
        assertThat(identities.snapshots.values().stream().distinct()).hasSize(1);
        assertThat(queries.listCommitted()).isEqualTo(result);
    }

    @Test
    void completeListingHasNoImplicitHistoryPageLimit() {
        seedDataset(1);
        inTransaction(() -> {
            for (int i = 0; i < 105; i++) {
                repository.insert(fact(Type.INTERNAL_TRANSFER, positions.reserveNext()));
            }
            return null;
        });
        var result = queries.listCommitted();
        assertThat(result.items()).hasSize(106);
        assertThat(result.items()).extracting(AdminFinancialTransactionQueries.Transaction::transactionId).doesNotHaveDuplicates();
        assertThat(identities.calls).hasSize(3);
        assertThat(identities.calls.values()).allMatch(count -> count == 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "one", "three", "bothPositive", "bothNegative", "unequal", "amountMismatch", "minimumDelta"})
    void corruptOrdinaryEntrySetsFailTheWholeListing(String corruption) {
        seedDataset(1);
        inTransaction(() -> {
            UUID id = fixtures.transaction("commit_seq", positions.reserveNext(), "amount_vnd", 100L);
            if (!corruption.equals("missing")) {
                fixtures.entry(id, source, corruption.equals("bothPositive") ? 100
                        : corruption.equals("minimumDelta") ? Long.MIN_VALUE
                        : corruption.equals("amountMismatch") ? -99 : -100);
            }
            if (!corruption.equals("missing") && !corruption.equals("one")) {
                fixtures.entry(id, destination, corruption.equals("bothNegative") ? -100
                        : corruption.equals("unequal") || corruption.equals("amountMismatch") ? 99 : 100);
            }
            if (corruption.equals("three")) {
                fixtures.entry(id, fixtures.account(), 1);
            }
            return null;
        });
        Snapshot before = snapshot();
        assertThatThrownBy(queries::listCommitted).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(identities.calls).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingControl", "missingEntry", "extraEntry", "negativeEntry", "wrongAmount", "wrongAccount",
            "wrongSupply", "wrongPosition", "counterBehind", "positionGap"})
    void inconsistentGenesisAndDatasetLinkageAreTechnicalFailures(String corruption) {
        inTransaction(() -> {
            UUID treasury = fixtures.account("account_type", "TREASURY");
            long position = corruption.equals("wrongPosition") ? 2 : 1;
            UUID id = fixtures.transaction("type", "GENESIS", "commit_seq", position);
            if (!corruption.equals("missingEntry")) {
                fixtures.entry(id, corruption.equals("wrongAccount") ? source : treasury,
                        corruption.equals("negativeEntry") ? -100 : corruption.equals("wrongAmount") ? 99 : 100);
            }
            if (corruption.equals("extraEntry")) {
                fixtures.entry(id, destination, 100);
            }
            if (!corruption.equals("missingControl")) {
                fixtures.dataset(new DatabaseFixtures.DatasetReferences(treasury, id),
                        "initial_supply_vnd", corruption.equals("wrongSupply") ? 101L : 100L, "last_commit_seq", position);
            }
            if (corruption.equals("counterBehind") || corruption.equals("positionGap")) {
                fixtures.transaction("commit_seq", corruption.equals("positionGap") ? 3L : 2L);
            }
            return null;
        });
        Snapshot before = snapshot();
        assertThatThrownBy(queries::listCommitted).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"positiveInfinity", "negativeInfinity"})
    void invalidCommittedMetadataFailsInsteadOfReturningPartialResults(String corruption) {
        seedDataset(1);
        inTransaction(() -> {
            UUID id = fixtures.transaction("commit_seq", positions.reserveNext(),
                    "reference", "finite-reference",
                    "committed_at", corruption.equals("positiveInfinity") ? OffsetDateTime.MAX
                            : OffsetDateTime.MIN);
            fixtures.entry(id, source, -100);
            fixtures.entry(id, destination, 100);
            return null;
        });
        assertThatThrownBy(queries::listCommitted).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @MethodSource("forbiddenRows")
    void schemaPreventsUnsupportedTypesAndInvalidHeaderFacts(String column, Object value, String state) {
        DataAccessException error = assertThrows(DataAccessException.class,
                () -> inTransaction(() -> fixtures.transaction(column, value)));
        LedgerPersistenceIntegrationTests.assertSqlState(error, state);
        assertThat(queries.listCommitted().items()).isEmpty();
    }

    static Stream<Arguments> forbiddenRows() {
        return Stream.of(Arguments.of("type", "CORRUPT", "23514"), Arguments.of("type", null, "23502"),
                Arguments.of("reference", "  ", "23514"), Arguments.of("reference", "\u2003", "23514"),
                Arguments.of("reference", null, "23502"),
                Arguments.of("reference", "x".repeat(65), "22001"), Arguments.of("reference", "bad\0ref", "22021"),
                Arguments.of("committed_at", null, "23502"), Arguments.of("amount_vnd", 0L, "23514"),
                Arguments.of("amount_vnd", -1L, "23514"), Arguments.of("currency", "USD", "23514"),
                Arguments.of("commit_seq", 0L, "23514"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void schemaPreventsZeroDeltasAndDuplicateParticipants(boolean duplicate) {
        seedDataset(1);
        DataAccessException error = assertThrows(DataAccessException.class, () -> inTransaction(() -> {
            UUID id = fixtures.transaction("commit_seq", positions.reserveNext());
            fixtures.entry(id, source, duplicate ? -100 : 0);
            fixtures.entry(id, source, 100);
            return null;
        }));
        LedgerPersistenceIntegrationTests.assertSqlState(error, duplicate ? "23505" : "23514");
        assertThat(queries.listCommitted().items()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrongId", "wrongKind", "duplicateNumber"})
    void missingOrInconsistentAccountIdentityFailsTechnically(String defect) {
        var seed = seedDataset(1);
        append(Type.INTERNAL_TRANSFER, 100, "transfer", TIME, source, destination);
        identities.lookup = id -> {
            var actual = identities.read(id).orElseThrow();
            if (id.equals(source)) {
                return switch (defect) {
                    case "missing" -> Optional.empty();
                    case "wrongId" -> Optional.of(new Identity(UUID.randomUUID(), actual.number(), actual.kind(), actual.customerId()));
                    case "wrongKind" -> Optional.of(new Identity(id, actual.number(), Kind.TREASURY, Optional.empty()));
                    default -> Optional.of(new Identity(id, number(seed.treasuryId()), actual.kind(), actual.customerId()));
                };
            }
            return Optional.of(actual);
        };
        assertThatThrownBy(queries::listCommitted).isInstanceOf(IllegalStateException.class);
        assertThat(snapshot()).isEqualTo(new Snapshot(2, 3, 2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENESIS", "DEMO_ALLOCATION"})
    void treasuryMustResolveAsTreasuryForInitializationAndAllocation(String type) {
        var seed = seedDataset(1);
        if (type.equals("DEMO_ALLOCATION")) {
            append(Type.DEMO_ALLOCATION, 100, "allocation", TIME, seed.treasuryId(), destination);
        }
        identities.lookup = id -> {
            var actual = identities.read(id).orElseThrow();
            return id.equals(seed.treasuryId())
                    ? Optional.of(new Identity(id, actual.number(), Kind.CUSTOMER, Optional.of(UUID.randomUUID())))
                    : Optional.of(actual);
        };
        assertThatThrownBy(queries::listCommitted).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ledgerSqlFailuresPropagateInsteadOfReturningEmpty() {
        seedDataset(1);
        ledgerJdbc.execute("ALTER TABLE public.ledger_entry RENAME TO unavailable_entries");
        var error = assertThrows(DataAccessException.class, queries::listCommitted);
        LedgerPersistenceIntegrationTests.assertSqlState(error, "42P01");
        assertThat(identities.calls).isEmpty();
    }

    @Test
    void accountSqlFailuresPropagateUnchanged() {
        seedDataset(1);
        identities.lookup = id -> {
            ledgerJdbc.execute("SELECT 1 / 0");
            throw new AssertionError("SQL must fail");
        };
        var error = assertThrows(DataAccessException.class, queries::listCommitted);
        LedgerPersistenceIntegrationTests.assertSqlState(error, "22012");
    }
}
