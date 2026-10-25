package com.finfast;

import com.finfast.ledger.api.LedgerQueries.Movement;
import com.finfast.ledger.api.LedgerQueries.PageRequest;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcCustomerHistoryFacts;
import com.finfast.ledger.internal.application.usecase.CustomerHistoryQueryService;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(45)
class LedgerHistorySnapshotIntegrationTests extends LedgerHistoryTestSupport {
    @Test
    void newTransfersAndAllocationsCannotEnterAnExistingTraversalButAppearInANewOne() {
        var seed = seedDataset(1);
        for (int i = 0; i < 5; i++) {
            append(Type.INTERNAL_TRANSFER, 1, "old-" + i, TIME, source, destination);
        }
        var first = history.history(source, new PageRequest(2, Optional.empty()));
        var firstCursor = codec.decode(first.nextCursor().orElseThrow());
        append(Type.INTERNAL_TRANSFER, 1, "new-transfer", TIME.minusSeconds(5), source, destination);
        append(Type.DEMO_ALLOCATION, 1, "new-allocation", TIME.minusSeconds(10), seed.treasuryId(), source);
        List<String> original = new ArrayList<>(first.items().stream().map(Movement::reference).toList());
        Optional<String> next = first.nextCursor();
        while (next.isPresent()) {
            assertThat(codec.decode(next.get()).highWater()).isEqualTo(firstCursor.highWater());
            var page = history.history(source, new PageRequest(2, next));
            original.addAll(page.items().stream().map(Movement::reference).toList());
            next = page.nextCursor();
        }
        assertThat(original).containsExactly("old-4", "old-3", "old-2", "old-1", "old-0").doesNotHaveDuplicates();
        assertThat(observed.boundReads.get()).isEqualTo(1);
        var fresh = history.history(source, new PageRequest(2, Optional.empty()));
        assertThat(fresh.items()).extracting(Movement::reference).containsExactly("new-allocation", "new-transfer");
        assertThat(codec.decode(fresh.nextCursor().orElseThrow()).highWater()).isGreaterThan(firstCursor.highWater());
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void firstPageBoundRowsAndIdentityReadsKeepOneSnapshotAcrossConcurrentCommit() throws Exception {
        seedDataset(1);
        append(Type.INTERNAL_TRANSFER, 1, "old-first", TIME, source, destination);
        append(Type.INTERNAL_TRANSFER, 1, "old-second", TIME, source, destination);
        var boundRead = new CountDownLatch(1);
        var committed = new CountDownLatch(1);
        observed.afterBound = () -> {
            boundRead.countDown();
            await(committed);
        };
        observed.beforePage = () -> {
            String current = ledgerJdbc.queryForObject("SELECT pg_current_snapshot()::text", String.class);
            assertThat(identities.snapshots.get(source)).isEqualTo(current);
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var reading = executor.submit(() -> history.history(source, new PageRequest(1, Optional.empty())));
            try {
                await(boundRead);
                inTransaction(() -> {
                    UUID newcomer = fixtures.account("account_number", "concurrent Customer");
                    append(Type.INTERNAL_TRANSFER, 1, "concurrent-newest", TIME, newcomer, source);
                    return null;
                });
            } finally {
                committed.countDown();
            }
            var page = reading.get(10, TimeUnit.SECONDS);
            assertThat(page.items()).extracting(Movement::reference).containsExactly("old-second");
            assertThat(codec.decode(page.nextCursor().orElseThrow()).highWater()).isEqualTo(3);
            assertThat(identities.snapshots.values().stream().distinct()).hasSize(1);
        }
        observed.afterBound = () -> { };
        var fresh = history.history(source, new PageRequest(1, Optional.empty()));
        assertThat(fresh.items()).extracting(Movement::reference).containsExactly("concurrent-newest");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pendingIncompleteAndCompletePostingIsInvisibleAndRollbackStaysAbsent(boolean commit) {
        seedDataset(1);
        var before = history.history(source, PageRequest.firstPage());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            transaction().executeWithoutResult(status -> {
                long position = positions.reserveNext();
                UUID id = fixtures.transaction("commit_seq", position);
                fixtures.entry(id, source, -100);
                try {
                    assertThat(executor.submit(() -> history.history(source, PageRequest.firstPage())).get(10, TimeUnit.SECONDS))
                            .isEqualTo(before);
                    fixtures.entry(id, destination, 100);
                    assertThat(executor.submit(() -> history.history(source, PageRequest.firstPage())).get(10, TimeUnit.SECONDS))
                            .isEqualTo(before);
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                if (!commit) {
                    status.setRollbackOnly();
                }
            });
        }
        assertThat(history.history(source, PageRequest.firstPage()).items()).hasSize(commit ? 1 : 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void heldCounterPreventsLateCommitFromEnteringAPreviouslyCapturedHistoryBound(boolean rollbackFirst) throws Exception {
        seedDataset(1);
        append(Type.INTERNAL_TRANSFER, 1, "old-first", TIME, source, destination);
        append(Type.INTERNAL_TRANSFER, 1, "old-second", TIME, source, destination);
        UUID otherDebit = inTransaction(() -> fixtures.account());
        UUID otherCredit = inTransaction(() -> fixtures.account());
        var firstReady = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holderPid = new AtomicInteger();
        var waiterPid = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> first = executor.submit(() -> transaction().executeWithoutResult(status -> {
                holderPid.set(ledgerJdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                append(Type.INTERNAL_TRANSFER, 1, "held-first", TIME.minusSeconds(50), source, destination);
                firstReady.countDown();
                await(release);
                if (rollbackFirst) {
                    status.setRollbackOnly();
                }
            }));
            try {
                await(firstReady);
                Future<?> second = executor.submit(() -> inTransaction(() -> {
                    waiterPid.set(ledgerJdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    secondStarted.countDown();
                    return append(Type.INTERNAL_TRANSFER, 1, "waiting-disjoint", TIME.plusSeconds(50), otherDebit, otherCredit);
                }));
                await(secondStarted);
                observeBlocked(waiterPid.get(), holderPid.get(), second);
                var old = history.history(source, new PageRequest(1, Optional.empty()));
                assertThat(codec.decode(old.nextCursor().orElseThrow()).highWater()).isEqualTo(3);
                assertThat(old.items()).extracting(Movement::reference).containsExactly("old-second");
                assertThat(history.history(otherDebit, PageRequest.firstPage()).items()).isEmpty();
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
                var last = history.history(source, new PageRequest(1, old.nextCursor()));
                assertThat(last.items()).extracting(Movement::reference).containsExactly("old-first");
                assertThat(last.nextCursor()).isEmpty();
                assertThat(history.history(source, PageRequest.firstPage()).items()).hasSize(rollbackFirst ? 2 : 3);
                assertThat(history.history(otherDebit, PageRequest.firstPage()).items())
                        .extracting(Movement::reference).containsExactly("waiting-disjoint");
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void serverEnforcesReadOnlySnapshotAndFactsAndIdentitiesShareItsResource() {
        seedDataset(1);
        append(Type.INTERNAL_TRANSFER, 1, "read-only", TIME, source, destination);
        readTransaction().executeWithoutResult(status -> {
            var snapshot = ledgerJdbc.queryForObject("SELECT pg_current_snapshot()::text", String.class);
            assertThat(history.history(source, PageRequest.firstPage()).items()).hasSize(1);
            assertThat(identities.snapshots.values()).containsOnly(snapshot);
            assertThat(ledgerJdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
            assertThat(ledgerJdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("repeatable read");
            var failure = assertThrows(DataAccessException.class, () -> fixtures.transaction());
            LedgerPersistenceIntegrationTests.assertSqlState(failure, "25006");
            status.setRollbackOnly();
        });
    }

    @Test
    void writeTransactionCannotExposeItsOwnPendingPostingThroughHistory() {
        seedDataset(1);
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            append(Type.INTERNAL_TRANSFER, 1, "rolled-back-misuse", TIME, source, destination);
            history.history(source, PageRequest.firstPage());
        })).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(history.history(source, PageRequest.firstPage()).items()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {Connection.TRANSACTION_READ_COMMITTED, Connection.TRANSACTION_SERIALIZABLE})
    void incompatibleReadIsolationFailsBeforeIdentityOrFinancialAccess(int isolation) {
        var wrong = readTransaction();
        wrong.setIsolationLevel(isolation);
        assertThatThrownBy(() -> wrong.execute(status -> history.history(source, PageRequest.firstPage())))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(identities.calls).isEmpty();
    }

    @Test
    void unproxiedAndUnboundReadsCannotMasqueradeAsACommittedSnapshot() {
        assertThatThrownBy(() -> new CustomerHistoryQueryService(observed, identities, codec).history(source, PageRequest.firstPage()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(jdbcFacts::highWater).isInstanceOf(IllegalTransactionStateException.class);
        readTransaction().executeWithoutResult(status -> {
            var mismatched = new JdbcCustomerHistoryFacts(new JdbcTemplate(isolatedDataSource()));
            assertThatThrownBy(mismatched::highWater).isInstanceOf(IllegalTransactionStateException.class);
            assertThatThrownBy(() -> mismatched.page(source, 1, OptionalLong.empty(), 2))
                    .isInstanceOf(IllegalTransactionStateException.class);
        });
        setMetadata();
        try {
            var unbound = new JdbcCustomerHistoryFacts(ledgerJdbc);
            assertThatThrownBy(unbound::highWater).isInstanceOf(IllegalTransactionStateException.class);
        } finally {
            clearMetadata();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"autocommit", "readWrite", "readCommitted"})
    void physicalResourceMustAgreeWithReadOnlyRepeatableReadThreadMetadata(String defect) throws Exception {
        var dataSource = ledgerJdbc.getDataSource();
        try (var connection = database.open()) {
            connection.setReadOnly(!defect.equals("readWrite"));
            connection.setTransactionIsolation(defect.equals("readCommitted")
                    ? Connection.TRANSACTION_READ_COMMITTED : Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(defect.equals("autocommit"));
            TransactionSynchronizationManager.bindResource(dataSource, new ConnectionHolder(connection));
            setMetadata();
            try {
                var reader = new JdbcCustomerHistoryFacts(ledgerJdbc);
                assertThatThrownBy(reader::highWater).isInstanceOf(IllegalTransactionStateException.class);
                assertThatThrownBy(() -> reader.page(source, 1, OptionalLong.empty(), 2))
                        .isInstanceOf(IllegalTransactionStateException.class);
            } finally {
                TransactionSynchronizationManager.unbindResource(dataSource);
                clearMetadata();
                if (!connection.getAutoCommit()) {
                    connection.rollback();
                }
            }
        }
    }

    private void observeBlocked(int waiter, int holder, Future<?> worker) {
        long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(ledgerJdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, holder, waiter))) {
                return;
            }
            assertThat(worker.isDone()).isFalse();
            Thread.onSpinWait();
        }
        throw new AssertionError("Expected observed PostgreSQL counter contention within deadline");
    }

    private static void setMetadata() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ);
    }

    private static void clearMetadata() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
    }
}
