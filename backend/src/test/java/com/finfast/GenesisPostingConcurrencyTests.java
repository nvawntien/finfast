package com.finfast;

import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.PostingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigInteger;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(45)
class GenesisPostingConcurrencyTests extends LedgerGenesisTestSupport {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentLedgerContributionsCannotFundTreasuryTwice(boolean rollbackFirst) throws Exception {
        UUID treasury = inTransaction(this::createTreasury);
        GenesisState before = genesisState();
        UUID losingMarker = UUID.randomUUID();
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();
        CountDownLatch firstPending = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<PostingResult.Pending> first = workers.submit(() -> transaction().execute(status -> {
                firstPid.set(pid());
                PostingResult.Pending result = genesis.initialize(treasury, new VndAmount(100));
                firstPending.countDown();
                await(releaseFirst);
                if (rollbackFirst) {
                    status.setRollbackOnly();
                }
                return result;
            }));
            await(firstPending);
            Future<PostingResult.Pending> second = workers.submit(() -> inTransaction(() -> {
                secondPid.set(pid());
                insertMarker(losingMarker); // Proves a losing caller's earlier writes also roll back.
                secondStarted.countDown();
                return genesis.initialize(treasury, new VndAmount(200));
            }));
            await(secondStarted);
            awaitDatabaseBlock(secondPid.get(), firstPid.get(), second);
            assertThat(accountsProbe.attempts.get()).as("both passed the absent-dataset check").isEqualTo(2);
            assertThat(genesisState()).isEqualTo(before);
            releaseFirst.countDown();
            PostingResult.Pending tentativeFirst = first.get(10, TimeUnit.SECONDS);
            PostingResult.Pending committed;
            if (rollbackFirst) {
                committed = second.get(10, TimeUnit.SECONDS);
                assertThat(inTransaction(() -> repository.findById(tentativeFirst.transactionId()))).isEmpty();
                assertThat(committed.transactionId()).isNotEqualTo(tentativeFirst.transactionId());
            } else {
                ExecutionException failure = assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
                assertThat(failure.getCause()).isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("Test Accounts");
                committed = tentativeFirst;
            }
            assertThat(markerCount(losingMarker)).isEqualTo(rollbackFirst ? 1 : 0);
            assertWinner(treasury, committed, rollbackFirst ? 200 : 100);
        } finally {
            releaseFirst.countDown();
            stop(workers);
        }
    }

    @Test
    void concurrentOuterTreasuryProvisioningLosesThroughRealUniqueConstraint() throws Exception {
        GenesisState before = genesisState();
        UUID losingMarker = UUID.randomUUID();
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();
        CountDownLatch firstPending = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<PostingResult.Pending> first = workers.submit(() -> inTransaction(() -> {
                firstPid.set(pid());
                PostingResult.Pending result = genesis.initialize(createTreasury(), new VndAmount(100));
                firstPending.countDown();
                await(releaseFirst);
                return result;
            }));
            await(firstPending);
            Future<PostingResult.Pending> second = workers.submit(() -> inTransaction(() -> {
                secondPid.set(pid());
                insertMarker(losingMarker);
                secondStarted.countDown();
                // Test-only provisioning attempt: the unique Treasury index blocks before Ledger is reached.
                return genesis.initialize(createTreasury(), new VndAmount(200));
            }));
            await(secondStarted);
            awaitDatabaseBlock(secondPid.get(), firstPid.get(), second);
            assertThat(genesisState()).isEqualTo(before);
            releaseFirst.countDown();
            PostingResult.Pending winner = first.get(10, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
            assertThat(failure.getCause()).isInstanceOfSatisfying(DataAccessException.class, error -> {
                LedgerPersistenceIntegrationTests.assertSqlState(error, "23505");
                assertThat(error.getMostSpecificCause().getMessage()).contains("uq_account_single_treasury");
            });
            assertThat(markerCount(losingMarker)).isZero();
            assertThat(accountsProbe.attempts.get()).isEqualTo(1);
            assertWinner(genesisState().treasuryId(), winner, 100);
        } finally {
            releaseFirst.countDown();
            stop(workers);
        }
    }

    private void assertWinner(UUID treasury, PostingResult.Pending winner, long supply) {
        GenesisState state = genesisState();
        assertThat(state.treasuries()).isEqualTo(1);
        assertThat(state.transactions()).isEqualTo(1);
        assertThat(state.entries()).isEqualTo(1);
        assertThat(state.datasets()).isEqualTo(1);
        assertThat(state.treasuryId()).isEqualTo(treasury);
        assertThat(state.transactionId()).isEqualTo(winner.transactionId());
        assertThat(state.supply()).isEqualTo(supply);
        assertThat(state.balance()).isEqualTo(BigInteger.valueOf(supply));
        assertThat(state.position()).isEqualTo(1);
        assertThat(balance(treasury)).isEqualTo(supply);
        assertThat(inTransaction(() -> repository.findById(winner.transactionId()).orElseThrow().entries()))
                .singleElement().satisfies(entry -> {
                    assertThat(entry.accountId()).isEqualTo(treasury);
                    assertThat(entry.deltaVnd()).isEqualTo(supply);
                });
    }

    private void insertMarker(UUID marker) {
        ledgerJdbc.update("INSERT INTO public.app_user (id, email_normalized, password_hash) VALUES (?, ?, ?)",
                marker, marker + "@example.test", "synthetic-test-only");
    }

    private long markerCount(UUID marker) {
        return ledgerJdbc.queryForObject("SELECT count(*) FROM public.app_user WHERE id = ?", Long.class, marker);
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
            assertThat(worker.isDone()).as("initialization must still be blocked in PostgreSQL").isFalse();
            Thread.onSpinWait();
        }
        throw new AssertionError("Expected a real PostgreSQL initialization lock wait before the deadline");
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).as("bounded fixture coordination").isTrue();
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
