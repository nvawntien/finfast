package com.finfast;

import com.finfast.ledger.api.PostingResult;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.dao.DataAccessException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(45)
class OrdinaryPostingConcurrencyTests extends LedgerOrdinaryPostingTestSupport {

    enum Scenario { SAME_SOURCE, OPPOSITE, ALLOCATIONS, DISJOINT }

    static Stream<Arguments> races() {
        return Stream.of(Arguments.of(Scenario.SAME_SOURCE, false), Arguments.of(Scenario.OPPOSITE, false),
                Arguments.of(Scenario.ALLOCATIONS, false), Arguments.of(Scenario.DISJOINT, false),
                Arguments.of(Scenario.DISJOINT, true));
    }

    @ParameterizedTest
    @MethodSource("races")
    void locksAndCounterSerializeCompleteFinancialMovements(Scenario scenario, boolean rollbackFirst) throws Exception {
        UUID third = fixtures.account();
        UUID fourth = fixtures.account();
        initialize(scenario == Scenario.ALLOCATIONS ? 100 : 1000);
        if (scenario != Scenario.ALLOCATIONS) {
            fund(source, 100);
        }
        if (scenario == Scenario.OPPOSITE) {
            fund(destination, 100);
        } else if (scenario == Scenario.DISJOINT) {
            fund(third, 100);
        }
        resetPairProbe();
        Supplier<PostingResult> firstAction = scenario == Scenario.ALLOCATIONS
                ? () -> allocations.postAllocation(allocation(source, 80))
                : () -> transfers.postTransfer(transfer(source, destination, scenario == Scenario.SAME_SOURCE ? 80 : 10));
        Supplier<PostingResult> secondAction = switch (scenario) {
            case ALLOCATIONS -> () -> allocations.postAllocation(allocation(destination, 80));
            case SAME_SOURCE -> () -> transfers.postTransfer(transfer(source, third, 80));
            case OPPOSITE -> () -> transfers.postTransfer(transfer(destination, source, 20));
            case DISJOINT -> () -> transfers.postTransfer(transfer(third, fourth, 20));
        };
        String before = financialState();
        long beforePosition = position();
        long beforeTransactions = snapshot().transactions();
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();
        AtomicLong secondThread = new AtomicLong();
        AtomicInteger secondWrites = new AtomicInteger();
        AtomicReference<UUID> firstTransaction = new AtomicReference<>();
        pairProbe.afterBalanceWrite = written -> {
            if (Thread.currentThread().threadId() == secondThread.get()) {
                secondWrites.set(written);
            }
        };
        CountDownLatch firstReady = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<PostingResult.Pending> first = workers.submit(() -> transaction().execute(status -> {
                firstPid.set(pid());
                PostingResult.Pending result = (PostingResult.Pending) firstAction.get();
                firstTransaction.set(result.transactionId());
                firstReady.countDown();
                await(releaseFirst);
                if (rollbackFirst) {
                    status.setRollbackOnly();
                }
                return result;
            }));
            await(firstReady);
            Future<PostingResult> second = workers.submit(() -> inTransaction(() -> {
                secondPid.set(pid());
                secondThread.set(Thread.currentThread().threadId());
                secondStarted.countDown();
                PostingResult result = secondAction.get();
                assertThat(repository.findById(firstTransaction.get()).isPresent()).isEqualTo(!rollbackFirst);
                return result;
            }));
            await(secondStarted);
            awaitDatabaseBlock(secondPid.get(), firstPid.get(), second);
            assertThat(financialState()).as("neither pending movement is committed").isEqualTo(before);
            assertThat(secondWrites.get()).as("disjoint pairs reach the counter only after both pending balance writes")
                    .isEqualTo(scenario == Scenario.DISJOINT ? 2 : 0);
            releaseFirst.countDown();
            PostingResult.Pending firstPending = first.get(10, TimeUnit.SECONDS);
            PostingResult secondResult = second.get(10, TimeUnit.SECONDS);
            boolean rejected = scenario == Scenario.SAME_SOURCE || scenario == Scenario.ALLOCATIONS;
            if (rejected) {
                assertThat(secondResult).isInstanceOfSatisfying(PostingResult.Rejected.class,
                        result -> assertThat(result.reason()).isSameAs(OrdinaryPostingTestAccounts.FixtureRejection.INSUFFICIENT_FUNDS));
                assertThat(position()).isEqualTo(beforePosition + 1);
                assertThat(snapshot().transactions()).isEqualTo(beforeTransactions + 1);
                assertThat(balance(scenario == Scenario.ALLOCATIONS ? treasury : source)).isEqualTo(20);
            } else {
                assertThat(secondResult).isInstanceOf(PostingResult.Pending.class);
                PostingResult.Pending secondPending = (PostingResult.Pending) secondResult;
                var storedSecond = inTransaction(() -> repository.findById(secondPending.transactionId()).orElseThrow());
                assertThat(storedSecond.commitSeq()).isEqualTo(beforePosition + (rollbackFirst ? 1 : 2));
                assertThat(storedSecond.recordedAt()).isEqualTo(firstPending.recordedAt()); // Time does not establish ordering.
                assertThat(snapshot().transactions()).isEqualTo(beforeTransactions + (rollbackFirst ? 1 : 2));
            }
            assertThat(inTransaction(() -> repository.findById(firstPending.transactionId()).isPresent())).isEqualTo(!rollbackFirst);
            if (!rollbackFirst) {
                assertThat(inTransaction(() -> repository.findById(firstPending.transactionId()).orElseThrow().commitSeq()))
                        .isEqualTo(beforePosition + 1);
            }
            if (scenario == Scenario.OPPOSITE) {
                var pairs = pairProbe.lockedPairs.stream().toList();
                assertThat(pairs).hasSize(2);
                assertThat(pairs.getFirst()).isEqualTo(pairs.getLast());
                assertThat(balance(source)).isEqualTo(110);
                assertThat(balance(destination)).isEqualTo(90);
            }
            assertFinancialInvariants();
        } finally {
            releaseFirst.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void accountOrCounterLockTimeoutRollsBackEveryPendingEffect(boolean disjoint) throws Exception {
        UUID third = fixtures.account();
        UUID fourth = fixtures.account();
        initialize(1000);
        fund(source, 100);
        fund(third, 100);
        resetPairProbe();
        String before = financialState();
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();
        AtomicLong secondThread = new AtomicLong();
        AtomicInteger secondWrites = new AtomicInteger();
        pairProbe.afterBalanceWrite = written -> {
            if (Thread.currentThread().threadId() == secondThread.get()) {
                secondWrites.set(written);
            }
        };
        CountDownLatch firstReady = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> transaction().execute(status -> {
                firstPid.set(pid());
                assertThat(transfers.postTransfer(transfer(source, destination, 10))).isInstanceOf(PostingResult.Pending.class);
                firstReady.countDown();
                await(releaseFirst);
                status.setRollbackOnly();
                return null;
            }));
            await(firstReady);
            Future<?> second = workers.submit(() -> inTransaction(() -> {
                secondPid.set(pid());
                secondThread.set(Thread.currentThread().threadId());
                ledgerJdbc.execute("SET LOCAL lock_timeout = '2s'");
                secondStarted.countDown();
                return transfers.postTransfer(transfer(disjoint ? third : source, disjoint ? fourth : destination, 20));
            }));
            await(secondStarted);
            awaitDatabaseBlock(secondPid.get(), firstPid.get(), second);
            assertThat(secondWrites.get()).isEqualTo(disjoint ? 2 : 0);
            ExecutionException failure = assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
            assertThat(failure.getCause()).isInstanceOfSatisfying(DataAccessException.class,
                    error -> LedgerPersistenceIntegrationTests.assertSqlState(error, "55P03"));
            assertThat(financialState()).isEqualTo(before);
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThat(financialState()).isEqualTo(before);
            assertFinancialInvariants();
        } finally {
            releaseFirst.countDown();
            stop(workers);
        }
    }

    private int pid() {
        return ledgerJdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private void awaitDatabaseBlock(int waiter, int holder, Future<?> worker) {
        assertThat(waiter).isNotEqualTo(holder);
        long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(ledgerJdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, holder, waiter))) {
                return;
            }
            assertThat(worker.isDone()).as("worker must be blocked inside PostgreSQL").isFalse();
            Thread.onSpinWait();
        }
        throw new AssertionError("Expected a real PostgreSQL posting lock wait before deadline");
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
}
