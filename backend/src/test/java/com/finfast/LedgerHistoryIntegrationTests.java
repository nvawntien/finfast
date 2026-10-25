package com.finfast;

import com.finfast.accounts.api.AccountIdentityQueries.Identity;
import com.finfast.accounts.api.AccountIdentityQueries.Kind;
import com.finfast.accounts.api.AccountNumber;
import com.finfast.ledger.api.LedgerQueries.Direction;
import com.finfast.ledger.api.LedgerQueries.Movement;
import com.finfast.ledger.api.LedgerQueries.Page;
import com.finfast.ledger.api.LedgerQueries.PageRequest;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Projection evidence on isolated financial fixtures; no production Accounts or History authorization claim. */
@Timeout(45)
class LedgerHistoryIntegrationTests extends LedgerHistoryTestSupport {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyHistoryExcludesGenesisRejectionsAndAudit(boolean initialized) {
        if (initialized) {
            seedDataset(1);
        }
        inTransaction(() -> {
            fixtures.outcome();
            fixtures.audit();
            return null;
        });
        Page page = history.history(source, PageRequest.firstPage());
        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isEmpty();
        assertThat(identities.calls).containsOnlyKeys(source);
        assertThat(observed.requestedCount).isEqualTo(21);
        assertThatThrownBy(() -> page.items().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"incoming", "outgoing", "allocation"})
    void exactReferencesSignedDirectionStoredTimestampAndMaxAmount(String scenario) {
        var seed = seedDataset(1);
        boolean allocation = scenario.equals("allocation");
        boolean incoming = !scenario.equals("outgoing");
        UUID debit = allocation ? seed.treasuryId() : incoming ? destination : source;
        UUID credit = incoming ? source : destination;
        var fact = append(allocation ? Type.DEMO_ALLOCATION : Type.INTERNAL_TRANSFER, Long.MAX_VALUE,
                " 😀e\u0301 exact original reference  ", TIME, debit, credit);
        var result = history.history(source, PageRequest.firstPage());
        assertThat(result.items()).containsExactly(new Movement(fact.reference(), allocation
                ? com.finfast.ledger.api.LedgerQueries.Type.DEMO_ALLOCATION : com.finfast.ledger.api.LedgerQueries.Type.INTERNAL_TRANSFER,
                incoming ? Direction.INCOMING : Direction.OUTGOING, fact.amount(), allocation ? "Treasury" : number(destination),
                TIME.truncatedTo(ChronoUnit.MICROS)));
        assertThat(result.nextCursor()).isEmpty();
        assertThat(identities.calls.values()).containsOnly(1);
        if (allocation) {
            assertThat(result.toString()).doesNotContain(number(seed.treasuryId()));
        }
    }

    @Test
    void mixedHistoryFiltersTheParticipantAndUsesPositionInsteadOfTimeOrUuidOrder() {
        var seed = seedDataset(1);
        var incoming = append(Type.INTERNAL_TRANSFER, 1, "incoming", TIME.plusSeconds(60), destination, source);
        var outgoing = append(Type.INTERNAL_TRANSFER, 2, "outgoing", TIME, source, destination);
        var allocation = append(Type.DEMO_ALLOCATION, 3, "allocation", TIME.minusSeconds(60), seed.treasuryId(), source);
        UUID other = inTransaction(() -> fixtures.account());
        append(Type.INTERNAL_TRANSFER, 4, "unrelated", TIME.plusSeconds(999), destination, other);
        inTransaction(() -> fixtures.outcome());
        var page = history.history(source, PageRequest.firstPage());
        assertThat(page.items()).extracting(Movement::reference).containsExactly(allocation.reference(), outgoing.reference(), incoming.reference());
        assertThat(page.items()).extracting(Movement::direction).containsExactly(Direction.INCOMING, Direction.OUTGOING, Direction.INCOMING);
        assertThat(page.items()).extracting(m -> m.amount().value()).containsExactly(3L, 2L, 1L);
        assertThat(page.nextCursor()).isEmpty();
        assertThat(identities.calls).hasSize(3);
        assertThat(identities.calls.values()).containsOnly(1);
        assertThat(identities.snapshots.values().stream().distinct()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 20, 100})
    void completeTraversalAcrossGapsAndTiedTimesHasNoDuplicatesOrMissingMovements(int limit) {
        seedDataset(1);
        List<String> expected = inTransaction(() -> {
            UUID firstOther = fixtures.account();
            UUID secondOther = fixtures.account();
            List<String> references = new ArrayList<>();
            for (int index = 0; index < 205; index++) {
                references.add(append(Type.INTERNAL_TRANSFER, index + 1, "eligible-" + index, TIME,
                        index % 2 == 0 ? source : destination, index % 2 == 0 ? destination : source).reference());
                if (index % 10 == 0) {
                    append(Type.INTERNAL_TRANSFER, 1, "unrelated-" + index, TIME, firstOther, secondOther);
                }
            }
            return references;
        });
        Collections.reverse(expected);
        List<String> actual = new ArrayList<>();
        Optional<String> cursor = Optional.empty();
        long originalBound = 0;
        int pages = 0;
        do {
            var page = history.history(source, new PageRequest(limit, cursor));
            assertThat(page.items()).hasSize(Math.min(limit, expected.size() - actual.size()));
            assertThat(observed.requestedCount).isEqualTo(limit + 1);
            actual.addAll(page.items().stream().map(Movement::reference).toList());
            cursor = page.nextCursor();
            if (cursor.isPresent()) {
                var state = codec.decode(cursor.get());
                if (originalBound == 0) {
                    originalBound = state.highWater();
                }
                assertThat(state.highWater()).isEqualTo(originalBound);
                assertThat(state.limit()).isEqualTo(limit);
                assertThat(state.ownerCustomerId()).isEqualTo(owner(source));
                assertThat(state.participantAccountId()).isEqualTo(source);
            }
            pages++;
            assertThat(pages).isLessThanOrEqualTo(205);
        } while (cursor.isPresent());
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        assertThat(observed.boundReads.get()).isEqualTo(1); // Continuations never capture another H.
        assertThat(identities.calls).hasSize(2);
        assertThat(identities.calls.values()).containsOnly(pages);
    }

    @ParameterizedTest
    @MethodSource("exactPages")
    void exactPageBoundariesHaveNoUnnecessaryFinalCursor(int count, int limit) {
        seedDataset(1);
        inTransaction(() -> {
            for (int i = 0; i < count; i++) {
                append(Type.INTERNAL_TRANSFER, 1, "R-" + i, TIME, source, destination);
            }
            return null;
        });
        var first = history.history(source, new PageRequest(limit, Optional.empty()));
        assertThat(first.items()).hasSize(Math.min(limit, count));
        assertThat(first.nextCursor().isPresent()).isEqualTo(count > limit);
        if (count > limit) {
            var last = history.history(source, new PageRequest(limit, first.nextCursor()));
            assertThat(last.items()).hasSize(count - limit);
            assertThat(last.nextCursor()).isEmpty();
        }
    }

    static Stream<Arguments> exactPages() {
        return Stream.of(Arguments.of(1, 1), Arguments.of(20, 20), Arguments.of(40, 20),
                Arguments.of(100, 100), Arguments.of(200, 100));
    }

    @Test
    void defaultFirstPageIsTwentyAndResultIsImmutable() {
        seedDataset(1);
        inTransaction(() -> {
            for (int i = 0; i < 21; i++) {
                append(Type.INTERNAL_TRANSFER, 1, "default-" + i, TIME, source, destination);
            }
            return null;
        });
        Page page = history.history(source, PageRequest.firstPage());
        assertThat(page.items()).hasSize(20);
        assertThat(page.nextCursor()).isPresent();
        assertThat(codec.decode(page.nextCursor().orElseThrow()).limit()).isEqualTo(20);
        assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 101, Integer.MAX_VALUE})
    void approvedPageRequestRejectsInvalidLimits(int limit) {
        assertThatThrownBy(() -> new PageRequest(limit, Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThat(observed.pageReads.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingCounterparty", "threeEntries", "bothPositive", "bothNegative", "mismatchedAmount",
            "unequal", "minimumDelta", "infiniteTimestamp"})
    void corruptedSelectedFactsFailWithoutReturningAPartialHistory(String defect) {
        seedDataset(1);
        inTransaction(() -> {
            UUID id = fixtures.transaction("commit_seq", positions.reserveNext(), "committed_at",
                    defect.equals("infiniteTimestamp") ? OffsetDateTime.MAX : DatabaseFixtures.RECORDED_AT);
            fixtures.entry(id, source, defect.equals("bothPositive") ? 100 : defect.equals("minimumDelta") ? Long.MIN_VALUE
                    : defect.equals("mismatchedAmount") ? -99 : -100);
            if (!defect.equals("missingCounterparty")) {
                fixtures.entry(id, destination, defect.equals("bothNegative") ? -100
                        : defect.equals("mismatchedAmount") || defect.equals("unequal") ? 99 : 100);
            }
            if (defect.equals("threeEntries")) {
                fixtures.entry(id, fixtures.account(), 1);
            }
            return null;
        });
        append(Type.INTERNAL_TRANSFER, 1, "newest-valid", TIME, source, destination);
        Snapshot before = snapshot();
        // The corrupt transaction is lookahead for a one-item page; it still cannot be silently skipped.
        assertThatThrownBy(() -> history.history(source, new PageRequest(1, Optional.empty())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingParticipant", "wrongParticipantId", "treasuryParticipant", "missingCounterparty",
            "wrongCounterpartyId", "treasuryTransfer", "duplicateNumber", "duplicateOwner"})
    void absentOrInconsistentAccountMetadataFailsTechnically(String defect) {
        var seed = seedDataset(1);
        append(Type.INTERNAL_TRANSFER, 1, "transfer", TIME, source, destination);
        identities.lookup = id -> {
            var actual = identities.read(id).orElseThrow();
            if ((defect.equals("missingParticipant") && id.equals(source))
                    || (defect.equals("missingCounterparty") && id.equals(destination))) {
                return Optional.empty();
            }
            if ((defect.equals("wrongParticipantId") && id.equals(source))
                    || (defect.equals("wrongCounterpartyId") && id.equals(destination))) {
                return Optional.of(new Identity(UUID.randomUUID(), actual.number(), actual.kind(), actual.customerId()));
            }
            if ((defect.equals("treasuryParticipant") && id.equals(source))
                    || (defect.equals("treasuryTransfer") && id.equals(destination))) {
                return Optional.of(new Identity(id, new AccountNumber(number(seed.treasuryId())), Kind.TREASURY, Optional.empty()));
            }
            if (id.equals(destination) && defect.equals("duplicateNumber")) {
                return Optional.of(new Identity(id, new AccountNumber(number(source)), Kind.CUSTOMER, actual.customerId()));
            }
            if (id.equals(destination) && defect.equals("duplicateOwner")) {
                return Optional.of(new Identity(id, actual.number(), Kind.CUSTOMER, Optional.of(owner(source))));
            }
            return Optional.of(actual);
        };
        assertThatThrownBy(() -> history.history(source, PageRequest.firstPage())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allocationWithCustomerInsteadOfTreasuryCounterpartyFailsTechnically() {
        seedDataset(1);
        append(Type.DEMO_ALLOCATION, 1, "bad-allocation", TIME, destination, source);
        assertThatThrownBy(() -> history.history(source, PageRequest.firstPage())).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingControl", "counterBehind", "counterAhead"})
    void inconsistentAuthoritativePostingBoundFailsTechnically(String defect) {
        if (!defect.equals("missingControl")) {
            seedDataset(1);
        }
        inTransaction(() -> {
            if (defect.equals("counterAhead")) {
                ledgerJdbc.update("UPDATE dataset_control SET last_commit_seq = last_commit_seq + 1");
            } else {
                UUID id = fixtures.transaction("commit_seq", 2L);
                fixtures.entry(id, source, -100);
                fixtures.entry(id, destination, 100);
            }
            return null;
        });
        assertThatThrownBy(() -> history.history(source, PageRequest.firstPage())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void boundedJdbcQuerySupportsLongMaximumWithoutIncrementingTheHighWater() {
        inTransaction(() -> {
            UUID id = fixtures.transaction("commit_seq", Long.MAX_VALUE);
            fixtures.entry(id, source, -100);
            fixtures.entry(id, destination, 100);
            return null;
        });
        readTransaction().executeWithoutResult(status -> {
            assertThat(jdbcFacts.page(source, Long.MAX_VALUE, OptionalLong.empty(), 2)).hasSize(1);
            assertThat(jdbcFacts.page(source, Long.MAX_VALUE, OptionalLong.of(Long.MAX_VALUE), 2)).isEmpty();
        });
    }

    @Test
    void sqlFailuresPropagateFromLedgerAndAccounts() {
        seedDataset(1);
        ledgerJdbc.execute("ALTER TABLE public.ledger_entry RENAME TO unavailable_history_entries");
        var ledgerError = assertThrows(DataAccessException.class, () -> history.history(source, PageRequest.firstPage()));
        LedgerPersistenceIntegrationTests.assertSqlState(ledgerError, "42P01");
        identities.lookup = id -> {
            ledgerJdbc.execute("SELECT 1 / 0");
            throw new AssertionError("SQL must fail");
        };
        var accountError = assertThrows(DataAccessException.class, () -> history.history(source, PageRequest.firstPage()));
        LedgerPersistenceIntegrationTests.assertSqlState(accountError, "22012");
    }
}
