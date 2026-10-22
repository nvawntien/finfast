package com.finfast;

import com.finfast.ledger.api.PostingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(40)
class LedgerReconciliationConcurrencyTests extends LedgerOrdinaryPostingTestSupport {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void heldReadOnlySnapshotRemainsConsistentAcrossCommitAtPartialOrCompleteWriteBoundary(boolean partial) throws Exception {
        initialize(100);
        fund(source, 60);
        var before = reconciliation();
        LedgerReconciliationVerifier verifier = new LedgerReconciliationVerifier(database);
        CountDownLatch writerHeld = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        AtomicInteger writerPid = new AtomicInteger();
        var workers = Executors.newSingleThreadExecutor();
        try (Connection reader = database.open(); Connection naive = database.open()) {
            reader.setReadOnly(true);
            reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            reader.setAutoCommit(false);
            var observer = IsolatedTestDatabase.jdbc(reader);
            var independentStatements = IsolatedTestDatabase.jdbc(naive);
            int readerPid = observer.queryForObject("SELECT pg_backend_pid()", Integer.class);
            assertThat(verifier.inspectSnapshot(reader)).isEqualTo(before);
            long naiveBalance = independentStatements.queryForObject(
                    "SELECT balance_vnd FROM account WHERE id = ?", Long.class, source);
            if (partial) {
                pairProbe.afterBalanceWrite = written -> {
                    if (written == 1) {
                        writerHeld.countDown();
                        await(releaseWriter);
                    }
                };
            }
            var writer = workers.submit(() -> inTransaction(() -> {
                writerPid.set(ledgerJdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                PostingResult result = transfers.postTransfer(transfer(source, destination, 10));
                assertThat(result).isInstanceOf(PostingResult.Pending.class);
                if (!partial) {
                    writerHeld.countDown();
                    await(releaseWriter);
                }
                return result;
            }));
            await(writerHeld);
            assertThat(writerPid.get()).isNotEqualTo(readerPid);
            assertThat(writer.isDone()).isFalse();
            assertThat(verifier.verify()).as("fresh committed snapshot ignores pending balances/facts/counter").isEqualTo(before);
            assertThat(verifier.inspectSnapshot(reader)).as("held snapshot excludes partial or complete pending writes").isEqualTo(before);
            releaseWriter.countDown();
            assertThat(writer.get(10, TimeUnit.SECONDS)).isInstanceOf(PostingResult.Pending.class);
            assertThat(verifier.inspectSnapshot(reader)).as("all repeated reads retain pre-commit RR snapshot").isEqualTo(before);
            // Deliberately demonstrate the false mismatch produced by separate READ COMMITTED statements.
            BigInteger naiveSigned = independentStatements.queryForObject(
                    "SELECT sum(delta_vnd) FROM ledger_entry WHERE account_id = ?",
                    (row, n) -> row.getBigDecimal(1).toBigIntegerExact(), source);
            assertThat(BigInteger.valueOf(naiveBalance)).isEqualTo(BigInteger.valueOf(60));
            assertThat(naiveSigned).isEqualTo(BigInteger.valueOf(50)).isNotEqualTo(BigInteger.valueOf(naiveBalance));
            var after = verifier.verify();
            assertThat(after.isConsistent()).as("%s", after.violations()).isTrue();
            assertThat(after.lastPosition()).isEqualTo(before.lastPosition() + 1);
            assertThat(after.transactionIds()).hasSize(before.transactionIds().size() + 1);
            assertThat(after.entryCount()).isEqualTo(before.entryCount().add(BigInteger.TWO));
            assertThat(after.totalBalance()).isEqualTo(before.totalBalance());
            assertThat(after.accounts()).filteredOn(a -> a.id().equals(source)).singleElement()
                    .satisfies(a -> assertThat(a.materialized()).isEqualTo(BigInteger.valueOf(50)));
            reader.rollback();
        } finally {
            releaseWriter.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void independentVerifierInsideCallerTransactionNeverMistakesTentativeWritesForCommittedFacts() {
        initialize(100);
        fund(source, 60);
        var before = reconciliation();
        pairProbe.afterBalanceWrite = written -> assertThat(reconciliation()).isEqualTo(before);
        transaction().executeWithoutResult(status -> {
            assertThat(transfers.postTransfer(transfer(source, destination, 10))).isInstanceOf(PostingResult.Pending.class);
            assertThat(reconciliation()).isEqualTo(before);
            status.setRollbackOnly();
        });
        assertThat(reconciliation()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mixedAllocationAndTransferReconcileAfterBothTransactionsFinish(boolean rollbackAllocation) throws Exception {
        initialize(200);
        fund(source, 50);
        var before = reconciliation();
        AtomicInteger allocationPid = new AtomicInteger();
        AtomicInteger transferPid = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var allocation = workers.submit(() -> transaction().execute(status -> {
                allocationPid.set(ledgerJdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                ready.countDown();
                await(start);
                PostingResult result = allocations.postAllocation(allocation(source, 20));
                if (rollbackAllocation) {
                    status.setRollbackOnly();
                }
                return result;
            }));
            var transfer = workers.submit(() -> inTransaction(() -> {
                transferPid.set(ledgerJdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                ready.countDown();
                await(start);
                return transfers.postTransfer(transfer(source, destination, 10));
            }));
            await(ready);
            assertThat(allocationPid.get()).isNotEqualTo(transferPid.get());
            start.countDown();
            PostingResult.Pending allocated = (PostingResult.Pending) allocation.get(10, TimeUnit.SECONDS);
            PostingResult.Pending transferred = (PostingResult.Pending) transfer.get(10, TimeUnit.SECONDS);
            var after = reconciliation();
            assertThat(after.isConsistent()).as("%s", after.violations()).isTrue();
            var expected = new java.util.ArrayList<>(before.transactionIds());
            expected.add(transferred.transactionId());
            if (!rollbackAllocation) {
                expected.add(allocated.transactionId());
            }
            assertThat(after.transactionIds()).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(after.lastPosition()).isEqualTo(before.lastPosition() + (rollbackAllocation ? 1 : 2));
            assertThat(after.entryCount()).isEqualTo(before.entryCount().add(BigInteger.valueOf(rollbackAllocation ? 2 : 4)));
            assertThat(balance(source)).isEqualTo(rollbackAllocation ? 40 : 60);
            assertThat(balance(destination)).isEqualTo(10);
            assertThat(balance(treasury)).isEqualTo(rollbackAllocation ? 150 : 130);
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).as("bounded writer/reader coordination").isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
