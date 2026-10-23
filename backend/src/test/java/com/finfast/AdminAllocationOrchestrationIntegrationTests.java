package com.finfast;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AccountQueries;
import com.finfast.administration.internal.application.usecase.AllocateDemoFundsService;
import com.finfast.administration.internal.application.dto.AllocationCommand;
import com.finfast.administration.internal.application.dto.AllocationResult;
import com.finfast.administration.internal.application.port.inbound.AllocateDemoFunds;
import com.finfast.common.money.VndAmount;
import com.finfast.idempotency.api.RequestOutcomes;
import com.finfast.identity.api.CurrentIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.sql.Connection;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(45)
class AdminAllocationOrchestrationIntegrationTests extends AdminAllocationTestSupport {

    @ParameterizedTest
    @ValueSource(longs = {1, 75, Long.MAX_VALUE})
    void proxyCommitsRealLedgerBalancesAndRequiredAuditOnOneConnection(long amount) {
        initialize(amount);
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        assertThat(AopUtils.isCglibProxy(service)).isTrue();
        assertThat(service).isSameAs(ledgerContext.getBean(AllocateDemoFunds.class));
        assertThat(AopUtils.isAopProxy(allocations)).isTrue();
        assertThat(AopUtils.isAopProxy(auditRepository)).isTrue();
        assertThat(AopUtils.isAopProxy(testOutcomes)).isTrue();
        pairProbe.afterBalanceWrite = ignored -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            probe.pids.add(ledgerJdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
        };
        var result = (AllocationResult.Pending) service.allocate(command(amount));
        var completion = (RequestOutcomes.Completion) result.outcome();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(probe.committed.get(scope()).outcome()).isEqualTo(completion);
        assertThat(probe.pids).hasSizeGreaterThanOrEqualTo(7);
        assertThat(probe.pids.stream().distinct()).hasSize(1);
        assertThat(balance(treasury)).isZero();
        assertThat(balance(destination)).isEqualTo(amount);
        assertThat(snapshot().transactions()).isEqualTo(2);
        assertThat(snapshot().entries()).isEqualTo(3);
        assertThat(position()).isEqualTo(2);
        assertThat(pairProbe.allocations).hasValue(1);
        assertThat(auditCount()).isEqualTo(1);
        var stored = inTransaction(() -> repository.findById(completion.transactionId()).orElseThrow());
        assertThat(completion.reference()).isEqualTo(stored.reference());
        assertThat(completion.recordedAt()).isEqualTo(stored.recordedAt());
        assertThat(completion.amount()).isEqualTo(new VndAmount(amount));
        assertThat(ledgerJdbc.queryForObject("SELECT admin_user_id FROM admin_audit_event", UUID.class)).isEqualTo(adminId);
        assertThat(ledgerJdbc.queryForObject("SELECT target_account_id FROM admin_audit_event", UUID.class)).isEqualTo(destination);
        assertThat(ledgerJdbc.queryForObject("SELECT transaction_id FROM admin_audit_event", UUID.class)).isEqualTo(completion.transactionId());
        assertThat(ledgerJdbc.queryForObject("SELECT reason FROM admin_audit_event", String.class)).isEqualTo(command(amount).reason());
        assertThat(ledgerJdbc.queryForObject("SELECT created_at FROM admin_audit_event", java.time.OffsetDateTime.class).toInstant())
                .isEqualTo(TIME.truncatedTo(ChronoUnit.MICROS));
        assertThat(probe.records).hasValue(1);
        // The test outcome collaborator writes NO request_outcome row: real durable Idempotency remains unverified.
        assertThat(ledgerJdbc.queryForObject("SELECT count(*) FROM request_outcome", Long.class)).isZero();
        assertFinancialInvariants();
    }

    @Test
    void independentReaderSeesNoTentativeEffectsAndCallerWaitsForOuterProxy() throws Exception {
        initialize(1000);
        String before = committedFinancialState();
        var inserted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        probe.afterAudit = () -> {
            assertThat(auditCount()).isEqualTo(1);
            inserted.countDown();
            try {
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError(error);
            }
        };
        AllocationCommand input = command(25);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> service.allocate(input));
            try {
                assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(future.isDone()).isFalse();
                assertThat(committedFinancialState()).isEqualTo(before);
                assertThat(probe.committed).isEmpty();
                try (Connection observer = database.open()) {
                    assertThat(probe.pids).doesNotContain(IsolatedTestDatabase.jdbc(observer)
                            .queryForObject("SELECT pg_backend_pid()", Integer.class));
                }
            } finally {
                release.countDown();
            }
            assertThat(future.get(10, TimeUnit.SECONDS)).isInstanceOf(AllocationResult.Pending.class);
        }
        assertThat(auditCount()).isEqualTo(1);
        assertThat(balance(destination)).isEqualTo(25);
        assertThat(probe.committed).hasSize(1);
    }

    @Test
    void requiredJoinsExistingOuterTransactionAndItsRollbackRemovesAllEffects() {
        initialize(1000);
        String before = committedFinancialState();
        var returned = transaction().execute(status -> {
            var pending = service.allocate(command(25));
            assertThat(pending).isInstanceOf(AllocationResult.Pending.class);
            assertThat(auditCount()).isEqualTo(1);
            assertThat(committedFinancialState()).isEqualTo(before);
            assertThat(probe.committed).isEmpty();
            status.setRollbackOnly();
            return pending;
        });
        assertThat(returned).isInstanceOf(AllocationResult.Pending.class);
        assertRolledBack(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"balance_one", "balance_two", "financial_transaction", "ledger_entry", "audit", "outcome"})
    void technicalFailureAtEachWriteBoundaryRollsBackEntireAllocation(String boundary) {
        initialize(1000);
        String before = committedFinancialState();
        switch (boundary) {
            case "balance_one", "balance_two" -> pairProbe.afterBalanceWrite = number -> {
                if (number == (boundary.equals("balance_one") ? 1 : 2)) {
                    throw new DataAccessResourceFailureException("synthetic balance failure");
                }
            };
            case "financial_transaction", "ledger_entry" -> failAfterInsert(boundary);
            case "audit" -> failAuditInsert();
            case "outcome" -> probe.beforeRecord = () -> {
                assertThat(auditCount()).isEqualTo(1);
                throw new DataAccessResourceFailureException("synthetic outcome persistence failure");
            };
            default -> throw new AssertionError(boundary);
        }
        assertThatThrownBy(() -> service.allocate(command(25))).isInstanceOf(DataAccessException.class);
        assertRolledBack(before);
    }

    @Test
    void laterSqlFailureAfterSuccessfulAuditInsertRollsEverythingBack() {
        initialize(1000);
        String before = committedFinancialState();
        probe.beforeRecord = () -> {
            assertThat(auditCount()).isEqualTo(1);
            ledgerJdbc.execute("SELECT 1 / 0");
        };
        assertThatThrownBy(() -> service.allocate(command(25))).isInstanceOf(DataAccessException.class);
        assertRolledBack(before);
    }

    @Test
    void checkedFailureReachingOuterServiceInterceptorAfterAuditAlsoRollsBack() {
        initialize(1000);
        String before = committedFinancialState();
        var failure = new IOException("synthetic checked technical failure");
        probe.afterAudit = () -> {
            assertThat(auditCount()).isEqualTo(1);
            AdminAllocationOrchestrationIntegrationTests.<RuntimeException>throwChecked(failure);
        };
        // The raw test audit decorator throws IOException directly into the service target. The service's
        // transaction interceptor sees that checked exception BEFORE CGLIB wraps the undeclared throwable.
        assertThatThrownBy(() -> service.allocate(command(25))).hasRootCause(failure);
        assertRolledBack(before);
    }

    @Test
    void caughtFailedMandatoryWriteCannotProduceSuccessfulOuterCommit() {
        initialize(1000);
        String before = committedFinancialState();
        probe.beforeRecord = () -> {
            try {
                ledgerContext.getBean(FailingWrite.class).fail();
            } catch (DataAccessException expected) {
                // Deliberately broken test collaborator catches the error AFTER its inner proxy marks rollback-only.
            }
        };
        assertThatThrownBy(() -> service.allocate(command(25))).isInstanceOf(UnexpectedRollbackException.class);
        assertRolledBack(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void beforeCommitFailurePreventsCallerReceivingATentativeSuccessfulResult(boolean transactionException) {
        initialize(1000);
        String before = committedFinancialState();
        probe.beforeRecord = () -> TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                if (transactionException) {
                    throw new TransactionSystemException("synthetic pre-commit failure");
                }
                throw new IllegalStateException("synthetic pre-commit failure");
            }
        });
        assertThatThrownBy(() -> service.allocate(command(25)))
                .isInstanceOf(transactionException ? TransactionSystemException.class : IllegalStateException.class);
        assertRolledBack(before);
    }

    @Test
    void sameKeyCanExecuteAfterRollbackAndSyntheticReplayAddsNoSecondPostingOrAudit() {
        initialize(1000);
        String before = committedFinancialState();
        probe.beforeRecord = () -> { throw new DataAccessResourceFailureException("synthetic outcome failure"); };
        assertThatThrownBy(() -> service.allocate(command(25))).isInstanceOf(DataAccessException.class);
        assertRolledBack(before);
        probe.beforeRecord = () -> { };
        var completed = (AllocationResult.Pending) service.allocate(command(25));
        String committed = committedFinancialState();
        int postingAttempts = pairProbe.allocations.get();
        var replay = service.allocate(command(25));
        assertThat(replay).isEqualTo(new AllocationResult.Replay(completed.outcome()));
        assertThat(pairProbe.allocations).hasValue(postingAttempts);
        assertThat(committedFinancialState()).isEqualTo(committed);
        assertThat(auditCount()).isEqualTo(1);
        assertThat(probe.committed).hasSize(1);
        assertThat(service.allocate(command(26))).isEqualTo(new AllocationResult.Conflict());
        assertThat(committedFinancialState()).isEqualTo(committed);
        assertThat(pairProbe.allocations).hasValue(postingAttempts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "frozen", "insufficient", "zero", "blank"})
    void businessRejectionFinalizesThroughFakeOutcomesWithoutFinancialOrAuditWrites(String reason) {
        initialize(1000);
        var input = command(25);
        if (reason.equals("missing")) {
            input = new AllocationCommand(input.key(), new AccountNumber("absent"), input.amount(), input.reason());
        } else if (reason.equals("frozen")) {
            ledgerJdbc.update("UPDATE public.account SET status = 'FROZEN' WHERE id = ?", destination);
        } else if (reason.equals("insufficient")) {
            input = command(1001);
        } else if (reason.equals("zero")) {
            input = command(0);
        } else {
            input = new AllocationCommand(input.key(), input.recipientNumber(), input.amount(), " \t");
        }
        String before = committedFinancialState();
        var result = (AllocationResult.Pending) service.allocate(input);
        assertThat(result.outcome()).isInstanceOf(RequestOutcomes.Rejection.class);
        assertThat(probe.committed.get(scope()).outcome()).isEqualTo(result.outcome());
        assertThat(committedFinancialState()).isEqualTo(before);
        assertThat(auditCount()).isZero();
        assertThat(probe.records).hasValue(1);
        assertThat(pairProbe.allocations).hasValue(reason.equals("frozen") || reason.equals("insufficient") ? 1 : 0);
        if (reason.equals("frozen")) {
            ledgerJdbc.update("UPDATE public.account SET status = 'ACTIVE' WHERE id = ?", destination);
            String afterUnfreeze = committedFinancialState();
            assertThat(service.allocate(input)).isEqualTo(new AllocationResult.Replay(result.outcome()));
            assertThat(committedFinancialState()).isEqualTo(afterUnfreeze);
            assertThat(pairProbe.allocations).hasValue(1);
            assertThat(probe.records).hasValue(1);
        }
    }

    @ParameterizedTest
    @EnumSource(RequestOutcomes.NotFinal.class)
    void nonFinalDecisionOnRealOuterTransactionHasNoWrites(RequestOutcomes.NotFinal state) {
        initialize(1000);
        String before = committedFinancialState();
        probe.forcedDecision = state;
        assertThat(service.allocate(command(25))).isEqualTo(AllocationResult.NotFinal.valueOf(state.name()));
        assertThat(committedFinancialState()).isEqualTo(before);
        assertThat(probe.records).hasValue(0);
        assertThat(pairProbe.allocations).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {TransactionDefinition.ISOLATION_REPEATABLE_READ, TransactionDefinition.ISOLATION_SERIALIZABLE})
    void incompatibleExistingIsolationFailsBeforeIdentityOrIdempotency(int isolation) {
        initialize(1000);
        var outer = transaction();
        outer.setIsolationLevel(isolation);
        assertThatThrownBy(() -> outer.execute(status -> service.allocate(command(25))))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(probe.pids).isEmpty();
    }

    @Test
    void readOnlyOuterTransactionCannotBeUsedForAllocationOrReplay() {
        initialize(1000);
        var outer = transaction();
        outer.setReadOnly(true);
        assertThatThrownBy(() -> outer.execute(status -> service.allocate(command(25))))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(probe.pids).isEmpty();
    }

    @Test
    void unproxiedServiceCannotPerformProtectedOperationsWithoutTransaction() {
        initialize(1000);
        var raw = new AllocateDemoFundsService(ledgerContext.getBean(CurrentIdentity.class), testOutcomes,
                ledgerContext.getBean(AccountQueries.class), allocations,
                auditRepository, ledgerContext.getBean(Clock.class));
        assertThatThrownBy(() -> raw.allocate(command(25))).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(probe.pids).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwChecked(Throwable error) throws T {
        throw (T) error;
    }
}
