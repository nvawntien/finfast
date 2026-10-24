package com.finfast;

import com.finfast.administration.internal.application.dto.AllocationCommand;
import com.finfast.administration.internal.application.dto.AllocationResult;
import com.finfast.idempotency.api.RequestOutcomes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Overlapping consumer calls with SCRIPTED decisions, not proof of PostgreSQL idempotency exclusion. */
@Timeout(45)
class AdminAllocationConcurrencyIntegrationTests extends AdminAllocationPhase4TestSupport {

    @ParameterizedTest
    @EnumSource(RequestOutcomes.NotFinal.class)
    void overlappingIdenticalRequestDoesNotFundOnScriptedNonFinalThenReplays(RequestOutcomes.NotFinal state) throws Exception {
        initialize(1000);
        var input = command(25);
        var auditInserted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        probe.afterAudit = () -> { auditInserted.countDown(); await(release); };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var winner = executor.submit(() -> service.allocate(input));
            try {
                await(auditInserted);
                outcomes.decision = (scope, intent) -> state;
                var duplicate = executor.submit(() -> service.allocate(input));
                assertThat(duplicate.get(8, TimeUnit.SECONDS)).isEqualTo(state == RequestOutcomes.NotFinal.PROCESSING
                        ? AllocationResult.NotFinal.PROCESSING : AllocationResult.NotFinal.UNKNOWN);
                assertThat(winner.isDone()).isFalse();
                assertThat(outcomes.recordings).isEmpty();
                assertThat(pairProbe.allocations).hasValue(1);
            } finally { release.countDown(); }
            var original = completion(winner.get(8, TimeUnit.SECONDS));
            outcomes.decision = (scope, intent) -> null;
            String committed = committedFinancialState();
            for (int retry = 0; retry < 3; retry++) {
                assertThat(service.allocate(input)).isEqualTo(new AllocationResult.Replay(original));
            }
            assertThat(committedFinancialState()).isEqualTo(committed);
            assertThat(outcomes.recordings).hasSize(1);
            assertThat(probe.records).hasValue(1);
            assertThat(pairProbe.allocations).hasValue(1);
            assertThat(outcomes.inspected()).allMatch(seen -> seen.equals(outcomes.inspected().getFirst()));
            assertAllocationFacts(1);
        }
    }

    @Test
    void overlappingChangedExactReasonIsNonFinalThenConflictsWithCommittedIntent() throws Exception {
        initialize(1000);
        var input = command(25);
        var changed = new AllocationCommand(input.key(), input.recipientNumber(), input.amount(), input.reason() + " ");
        var auditInserted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        probe.afterAudit = () -> { auditInserted.countDown(); await(release); };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var winner = executor.submit(() -> service.allocate(input));
            try {
                await(auditInserted);
                outcomes.decision = (scope, intent) -> RequestOutcomes.NotFinal.UNKNOWN;
                assertThat(executor.submit(() -> service.allocate(changed)).get(8, TimeUnit.SECONDS))
                        .isEqualTo(AllocationResult.NotFinal.UNKNOWN);
            } finally { release.countDown(); }
            var original = completion(winner.get(8, TimeUnit.SECONDS));
            outcomes.decision = (scope, intent) -> null;
            String committed = committedFinancialState();
            var saved = probe.committed.get(scope());
            assertThat(service.allocate(changed)).isEqualTo(new AllocationResult.Conflict());
            assertThat(service.allocate(input)).isEqualTo(new AllocationResult.Replay(original));
            assertThat(probe.committed.get(scope())).isSameAs(saved);
            assertThat(outcomes.inspected().get(0).intent()).isNotEqualTo(outcomes.inspected().get(1).intent());
            assertThat(outcomes.recordings).hasSize(1);
            assertThat(pairProbe.allocations).hasValue(1);
            assertThat(committedFinancialState()).isEqualTo(committed);
            assertAllocationFacts(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {" exact Key", " Exact Key "})
    void separateExactKeysCanReachPostingConcurrentlyForTheSameRecipient(String otherKey) throws Exception {
        initialize(1000);
        var first = withKey(" exact Key ");
        var second = withKey(otherKey);
        var bothReachedPosting = new CyclicBarrier(2);
        beforePosting = () -> meet(bothReachedPosting);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> service.allocate(first));
            var two = executor.submit(() -> service.allocate(second));
            var resultOne = completion(one.get(12, TimeUnit.SECONDS));
            var resultTwo = completion(two.get(12, TimeUnit.SECONDS));
            assertThat(resultOne.transactionId()).isNotEqualTo(resultTwo.transactionId());
            assertThat(outcomes.inspected()).extracting(seen -> seen.scope().key().value())
                    .containsExactlyInAnyOrder(first.key().value(), otherKey);
            assertThat(probe.committed).hasSize(2);
            assertThat(outcomes.recordings).hasSize(2);
            assertAllocationFacts(2);
        }
    }

    @Test
    void differentTrustedAdministratorsCanUseTheSameExactKeyConcurrently() throws Exception {
        initialize(1000);
        var secondAdmin = inTransaction(() -> fixtures.user("role", "ADMIN"));
        var input = command(25);
        var bothReachedPosting = new CyclicBarrier(2);
        beforePosting = () -> meet(bothReachedPosting);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> service.allocate(input));
            var two = executor.submit(() -> currentAdmin.as(secondAdmin, () -> service.allocate(input)));
            completion(one.get(12, TimeUnit.SECONDS));
            completion(two.get(12, TimeUnit.SECONDS));
            assertThat(outcomes.inspected()).extracting(seen -> seen.scope().initiatorUserId())
                    .containsExactlyInAnyOrder(adminId, secondAdmin);
            assertThat(outcomes.inspected()).allMatch(seen -> seen.scope().key().equals(input.key())
                    && seen.scope().operation() == RequestOutcomes.Operation.DEMO_ALLOCATION);
            assertThat(outcomes.inspected().get(0).intent()).isNotEqualTo(outcomes.inspected().get(1).intent());
            assertThat(ledgerJdbc.queryForList("SELECT admin_user_id FROM admin_audit_event", java.util.UUID.class))
                    .containsExactlyInAnyOrder(adminId, secondAdmin);
            assertThat(probe.committed).hasSize(2);
            assertAllocationFacts(2);
        }
    }

    @Test
    void failedWinnerLeavesNoFinalStateAndOriginalExactIntentCanExecuteThenReplay() throws Exception {
        initialize(1000);
        String before = committedFinancialState();
        var input = command(25);
        var auditInserted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        probe.afterAudit = () -> { auditInserted.countDown(); await(release); };
        probe.beforeRecord = () -> { throw new DataAccessResourceFailureException("synthetic outcome boundary failure"); };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var winner = executor.submit(() -> service.allocate(input));
            try {
                await(auditInserted);
                outcomes.decision = (scope, intent) -> RequestOutcomes.NotFinal.PROCESSING;
                assertThat(executor.submit(() -> service.allocate(input)).get(8, TimeUnit.SECONDS))
                        .isEqualTo(AllocationResult.NotFinal.PROCESSING);
            } finally { release.countDown(); }
            assertThatThrownBy(() -> winner.get(8, TimeUnit.SECONDS)).hasCauseInstanceOf(DataAccessResourceFailureException.class);
        }
        assertThat(committedFinancialState()).isEqualTo(before);
        assertThat(probe.committed).isEmpty();
        assertAllocationFacts(0);
        outcomes.decision = (scope, intent) -> null;
        probe.beforeRecord = () -> { };
        probe.afterAudit = () -> { };
        var original = completion(service.allocate(input));
        assertThat(service.allocate(input)).isEqualTo(new AllocationResult.Replay(original));
        assertThat(outcomes.inspected()).allMatch(seen -> seen.equals(outcomes.inspected().getFirst()));
        assertThat(outcomes.attemptedOutcomes).allMatch(RequestOutcomes.Completion.class::isInstance);
        assertThat(probe.committed).hasSize(1);
        assertAllocationFacts(1);
    }

    @Test
    void interruptingAnInFlightAttemptAfterAuditUnwindsBeforeAnOriginalKeyRetry() throws Exception {
        initialize(1000);
        String before = committedFinancialState();
        var input = command(25);
        var auditInserted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var unwound = new CountDownLatch(1);
        probe.afterAudit = () -> { auditInserted.countDown(); await(release); };
        try (var executor = Executors.newSingleThreadExecutor()) {
            var interrupted = executor.submit(() -> {
                try { return service.allocate(input); } finally { unwound.countDown(); }
            });
            try {
                await(auditInserted);
                assertThat(interrupted.cancel(true)).isTrue();
                await(unwound);
            } finally { release.countDown(); }
        }
        assertThat(committedFinancialState()).isEqualTo(before);
        assertThat(probe.committed).isEmpty();
        assertThat(outcomes.recordings).isEmpty();
        probe.afterAudit = () -> { };
        completion(service.allocate(input));
        assertThat(outcomes.inspected()).allMatch(seen -> seen.equals(outcomes.inspected().getFirst()));
        assertAllocationFacts(1);
    }

    @Test
    void allocationDoesNotConsumeOrOverwriteAFinalOutcomeFromAnotherOperation() {
        initialize(1000);
        var input = command(25);
        var otherScope = new RequestOutcomes.Scope(adminId, RequestOutcomes.Operation.INTERNAL_TRANSFER, input.key());
        // Opaque unrelated-operation sentinel only; this is not a transfer canonicalization vector/provider.
        var other = new Stored(new RequestOutcomes.CanonicalIntent((short) 1, "{\"testSentinel\":\"unrelated intent\"}"),
                new RequestOutcomes.Rejection("TEST_TRANSFER_REJECTION", "Synthetic unrelated operation"));
        probe.committed.put(otherScope, other);
        completion(service.allocate(input));
        assertThat(outcomes.inspected()).singleElement().satisfies(seen -> {
            assertThat(seen.scope()).isEqualTo(scope());
            assertThat(seen.scope().key().value()).isEqualTo(input.key().value());
        });
        assertThat(probe.committed.get(otherScope)).isSameAs(other);
        assertThat(probe.committed).hasSize(2);
        assertAllocationFacts(1);
    }

    private AllocationCommand withKey(String key) {
        var input = command(25);
        return new AllocationCommand(new RequestOutcomes.Key(key), input.recipientNumber(), input.amount(), input.reason());
    }
}
