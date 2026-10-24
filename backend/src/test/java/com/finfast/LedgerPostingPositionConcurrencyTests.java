package com.finfast;

import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(45)
class LedgerPostingPositionConcurrencyTests extends LedgerPersistenceTestSupport {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reservationsWaitForCommitOrRollback(boolean rollbackFirst) throws Exception {
        seedDataset(1);
        Snapshot before = snapshot();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();
        CountDownLatch firstReady = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> first = executor.submit(() -> transaction().execute(status -> {
                firstPid.set(pid());
                long sequence = positions.reserveNext();
                repository.insert(pair(Type.INTERNAL_TRANSFER, firstId, "FIRST", sequence,
                        UUID.randomUUID(), UUID.randomUUID(), destination, TIME.plusSeconds(60)));
                firstReady.countDown();
                await(releaseFirst);
                if (rollbackFirst) {
                    status.setRollbackOnly();
                }
                return sequence;
            }));
            await(firstReady);
            Future<Long> second = executor.submit(() -> inTransaction(() -> {
                secondPid.set(pid());
                secondStarted.countDown();
                long sequence = positions.reserveNext(); // Must wait in PostgreSQL, not on a Java monitor.
                assertThat(repository.findById(firstId).isPresent()).isEqualTo(!rollbackFirst);
                repository.insert(pair(Type.INTERNAL_TRANSFER, secondId, "SECOND", sequence,
                        UUID.randomUUID(), UUID.randomUUID(), destination, TIME.minusSeconds(60)));
                return sequence;
            }));
            await(secondStarted);
            awaitDatabaseBlock(secondPid.get(), firstPid.get(), second);
            assertThat(secondPid.get()).isNotEqualTo(firstPid.get());
            assertThat(snapshot()).as("the uncommitted reservation and complete facts are invisible").isEqualTo(before);
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(2);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(rollbackFirst ? 2 : 3);
            assertThat(snapshot()).isEqualTo(rollbackFirst ? new Snapshot(2, 3, 2) : new Snapshot(3, 5, 3));
            assertThat(inTransaction(() -> repository.findById(firstId).isPresent())).isEqualTo(!rollbackFirst);
            assertThat(inTransaction(() -> repository.findById(secondId))).isPresent();
            assertThat(ledgerJdbc.queryForList("SELECT commit_seq FROM financial_transaction ORDER BY commit_seq", Long.class))
                    .containsExactlyElementsOf(rollbackFirst ? java.util.List.of(1L, 2L) : java.util.List.of(1L, 2L, 3L));
            // Recorded timestamps intentionally run backwards; position order comes from the held singleton lock.
            assertThat(ledgerJdbc.queryForObject("SELECT count(*) = count(DISTINCT commit_seq) FROM financial_transaction", Boolean.class))
                    .isTrue();
        } finally {
            releaseFirst.countDown();
            stop(executor);
        }
    }

    @Test
    void lockTimeoutIsTechnicalAndRollsBack() throws Exception {
        seedDataset(1);
        Snapshot before = snapshot();
        CountDownLatch firstReady = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = executor.submit(() -> transaction().execute(status -> {
                long sequence = positions.reserveNext();
                repository.insert(fact(Type.INTERNAL_TRANSFER, sequence));
                firstReady.countDown();
                await(releaseFirst);
                status.setRollbackOnly();
                return null;
            }));
            await(firstReady);
            DataAccessException error = assertThrows(DataAccessException.class, () -> inTransaction(() -> {
                ledgerJdbc.execute("SET LOCAL lock_timeout = '200ms'");
                return positions.reserveNext();
            }));
            LedgerPersistenceIntegrationTests.assertSqlState(error, "55P03");
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThat(snapshot()).isEqualTo(before);
        } finally {
            releaseFirst.countDown();
            stop(executor);
        }
    }

    private int pid() {
        return ledgerJdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private void awaitDatabaseBlock(int waiter, int holder, Future<?> worker) {
        long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(ledgerJdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, holder, waiter))) {
                return;
            }
            assertThat(worker.isDone()).as("worker must not finish before the held transaction ends").isFalse();
            Thread.onSpinWait();
        }
        throw new AssertionError("Expected an observed PostgreSQL blocking relationship before the deadline");
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
