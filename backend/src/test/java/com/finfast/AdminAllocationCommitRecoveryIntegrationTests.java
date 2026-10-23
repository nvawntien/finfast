package com.finfast;

import com.finfast.administration.internal.application.dto.AllocationResult;
import com.finfast.common.error.ApplicationException;
import com.finfast.common.error.ErrorCode;
import com.finfast.idempotency.api.RequestOutcomes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Real JDBC boundary observations; simulated Idempotency replay is not durable recovery evidence. */
@Timeout(45)
class AdminAllocationCommitRecoveryIntegrationTests extends AdminAllocationPhase4TestSupport {
    private final AllocationJdbcCommitProbe commits = new AllocationJdbcCommitProbe();

    @Override protected DataSource isolatedDataSource() { return commits.wrap(super.isolatedDataSource()); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preCommitTransactionExceptionReproducesDefaultCleanupRiskAndConfiguredRollback(boolean rollbackOnCommitFailure) {
        initialize(1000);
        String before = committedFinancialState();
        commits.transactions.clear();
        ((JdbcTransactionManager) manager).setRollbackOnCommitFailure(rollbackOnCommitFailure);
        var status = new AtomicInteger(-1);
        var failure = new TransactionSystemException("synthetic beforeCommit failure");
        probe.beforeRecord = () -> TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) { throw failure; }
            @Override public void afterCompletion(int completion) { status.set(completion); }
        });
        assertThat(catchThrowable(() -> service.allocate(command(25)))).isSameAs(failure);
        var trace = onlyTrace();
        assertThat(trace.commitCalls).as("COMMIT was never attempted before this callback failure").isZero();
        assertThat(trace.rollbackCalls).isEqualTo(rollbackOnCommitFailure ? 1 : 0);
        assertThat(trace.events).contains("autocommit:true");
        assertThat(probe.committed).isEmpty();
        if (rollbackOnCommitFailure) {
            assertThat(trace.delegateRollbackReturned).isTrue();
            assertThat(status).hasValue(TransactionSynchronization.STATUS_ROLLED_BACK);
            assertThat(committedFinancialState()).isEqualTo(before);
            assertAllocationFacts(0);
        } else {
            // Diagnostic proof of the unsafe default, not a relaxed rollback acceptance assertion.
            assertThat(status).hasValue(TransactionSynchronization.STATUS_UNKNOWN);
            assertThat(trace.events).containsExactly("autocommit:false", "autocommit:true");
            assertThat(committedFinancialState()).isNotEqualTo(before);
            assertAllocationFacts(1);
        }
    }

    @Test
    void failedRollbackCanReplaceTheOriginalErrorAndStillCommitOnSyntheticUsableConnectionCleanup() {
        initialize(1000);
        commits.transactions.clear();
        commits.nextFault.set(AllocationJdbcCommitProbe.Fault.ROLLBACK);
        var original = new TransactionSystemException("original beforeCommit failure");
        var status = new AtomicInteger(-1);
        probe.beforeRecord = () -> TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) { throw original; }
            @Override public void afterCompletion(int completion) { status.set(completion); }
        });
        var exposed = catchThrowable(() -> service.allocate(command(25)));
        assertThat(exposed).isInstanceOf(DataAccessException.class).isNotSameAs(original).hasRootCauseInstanceOf(SQLException.class);
        var trace = onlyTrace();
        assertThat(trace.commitCalls).isZero();
        assertThat(trace.rollbackCalls).isEqualTo(1);
        assertThat(trace.delegateRollbackReturned).isFalse();
        assertThat(status).hasValue(TransactionSynchronization.STATUS_UNKNOWN);
        assertThat(probe.committed).isEmpty();
        // This injection fails rollback without breaking the connection; restoring autocommit commits it.
        // A genuinely broken connection has a different/uncertain outcome, not this deterministic result.
        assertThat(trace.events).containsExactly("autocommit:false", "rollback:attempt", "autocommit:true");
        assertAllocationFacts(1);
    }

    @Test
    void failureBeforeDelegateCommitRollsBackAndAllowsTheOriginalExactIntentInANewTransaction() {
        initialize(1000);
        String before = committedFinancialState();
        commits.transactions.clear();
        commits.nextFault.set(AllocationJdbcCommitProbe.Fault.BEFORE_COMMIT_DELEGATE);
        var input = command(25);
        assertThatThrownBy(() -> service.allocate(input)).isInstanceOf(DataAccessException.class);
        var trace = onlyTrace();
        assertThat(trace.commitCalls).isEqualTo(1);
        assertThat(trace.delegateCommitReturned).isFalse();
        assertThat(trace.rollbackCalls).isEqualTo(1);
        assertThat(trace.delegateRollbackReturned).isTrue();
        assertThat(committedFinancialState()).isEqualTo(before);
        assertThat(probe.committed).isEmpty();
        assertAllocationFacts(0);
        var original = completion(service.allocate(input));
        assertThat(service.allocate(input)).isEqualTo(new AllocationResult.Replay(original));
        assertThat(outcomes.inspected()).allMatch(seen -> seen.equals(outcomes.inspected().getFirst()));
        assertAllocationFacts(1);
    }

    @Test
    void realPostgresDeferredAuditFailureAtCommitLeavesNoCommittedEffects() {
        initialize(1000);
        ledgerJdbc.execute("""
                CREATE FUNCTION public.test_deferred_allocation_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'synthetic deferred audit failure' USING ERRCODE = 'P0001'; END; $$
                """);
        ledgerJdbc.execute("""
                CREATE CONSTRAINT TRIGGER test_deferred_allocation_audit AFTER INSERT ON public.admin_audit_event
                DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.test_deferred_allocation_audit()
                """);
        String before = committedFinancialState();
        commits.transactions.clear();
        probe.beforeRecord = () -> assertThat(auditCount()).as("audit INSERT succeeded before COMMIT").isEqualTo(1);
        var error = catchThrowable(() -> service.allocate(command(25)));
        assertThat(error).isInstanceOf(RuntimeException.class).hasRootCauseInstanceOf(SQLException.class);
        assertThat(((SQLException) deepest(error)).getSQLState()).isEqualTo("P0001");
        var trace = onlyTrace();
        assertThat(trace.commitCalls).isEqualTo(1);
        assertThat(trace.delegateCommitReturned).isFalse();
        assertThat(trace.rollbackCalls).isEqualTo(1);
        assertThat(committedFinancialState()).isEqualTo(before);
        assertThat(probe.committed).isEmpty();
        assertAllocationFacts(0);
    }

    @Test
    void commitAcknowledgementLossCannotBeReclassifiedAsRollbackOrFinalBusinessRejection() {
        initialize(1000);
        commits.transactions.clear();
        commits.nextFault.set(AllocationJdbcCommitProbe.Fault.AFTER_COMMIT_DELEGATE);
        var status = new AtomicInteger(-1);
        probe.beforeRecord = () -> TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int completion) { status.set(completion); }
        });
        var input = command(25);
        assertThatThrownBy(() -> service.allocate(input)).isInstanceOf(DataAccessException.class);
        var trace = onlyTrace();
        assertThat(trace.delegateCommitReturned).isTrue();
        assertThat(trace.rollbackCalls).isEqualTo(1);
        assertThat(trace.delegateRollbackReturned).isTrue(); // Does NOT undo the successful earlier commit.
        assertThat(status).as("Spring's rollback callback status is not evidence the earlier COMMIT was undone")
                .hasValue(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertAllocationFacts(1);
        assertThat(probe.committed).as("afterCommit memory map never ran; absence cannot authorize a retry").isEmpty();
        var candidate = outcomes.attemptedOutcomes.element();
        assertThat(candidate).isInstanceOf(RequestOutcomes.Completion.class);
        String committed = committedFinancialState();
        outcomes.decision = (scope, intent) -> RequestOutcomes.NotFinal.UNKNOWN;
        assertThat(service.allocate(input)).isEqualTo(AllocationResult.NotFinal.UNKNOWN);
        assertThat(outcomes.recordings).hasSize(1);
        assertThat(committedFinancialState()).isEqualTo(committed);
        // Replay is scripted only after the fixture's independent DB observation; real durable lookup is BLOCKED.
        outcomes.decision = (scope, intent) -> new RequestOutcomes.Replay(candidate);
        assertThat(service.allocate(input)).isEqualTo(new AllocationResult.Replay(candidate));
        assertThat(outcomes.inspected()).allMatch(seen -> seen.equals(outcomes.inspected().getFirst()));
        assertThat(pairProbe.allocations).hasValue(1);
        assertThat(committedFinancialState()).isEqualTo(committed);
    }

    @Test
    void afterCommitCallbackFailureIsAlreadyCommittedEvenWithRollbackOnCommitFailureEnabled() {
        initialize(1000);
        commits.transactions.clear();
        var failure = new IllegalStateException("synthetic afterCommit observer failure");
        var status = new AtomicInteger(-1);
        probe.beforeRecord = () -> TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { throw failure; }
            @Override public void afterCompletion(int completion) { status.set(completion); }
        });
        assertThat(catchThrowable(() -> service.allocate(command(25)))).isSameAs(failure);
        assertThat(onlyTrace().delegateCommitReturned).isTrue();
        assertThat(onlyTrace().rollbackCalls).isZero();
        assertThat(status).hasValue(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(probe.committed).as("later fake outcome afterCommit callback was not invoked").isEmpty();
        assertAllocationFacts(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void technicalFailureAroundPostingHasNoAuditOrOutcomeAndCanRetryAfterConfirmedRollback(boolean afterLedger) {
        initialize(1000);
        String before = committedFinancialState();
        commits.transactions.clear();
        var failure = new org.springframework.dao.DataAccessResourceFailureException("synthetic posting boundary failure");
        Runnable inject = () -> { throw failure; };
        if (afterLedger) { afterPosting = inject; } else { beforePosting = inject; }
        var input = command(25);
        assertThat(catchThrowable(() -> service.allocate(input))).isSameAs(failure);
        assertThat(onlyTrace().commitCalls).isZero();
        assertThat(onlyTrace().delegateRollbackReturned).isTrue();
        assertThat(committedFinancialState()).isEqualTo(before);
        assertThat(outcomes.recordings).isEmpty();
        assertAllocationFacts(0);
        beforePosting = () -> { };
        afterPosting = () -> { };
        completion(service.allocate(input));
        assertThat(outcomes.inspected()).allMatch(seen -> seen.equals(outcomes.inspected().getFirst()));
        assertAllocationFacts(1);
    }

    @Test
    void lostCallerResponseRequiresFreshAuthorizationAndOriginalKeyReplayWithoutMoreWrites() {
        initialize(1000);
        commits.transactions.clear();
        assertThat(AopUtils.isCglibProxy(service)).isTrue();
        var input = command(25);
        var lostResponse = new IllegalStateException("synthetic caller response loss after proxy completion");
        assertThat(catchThrowable(() -> {
            completion(service.allocate(input));
            throw lostResponse;
        })).isSameAs(lostResponse);
        assertThat(onlyTrace().delegateCommitReturned).isTrue();
        var original = probe.committed.get(scope()).outcome();
        String committed = committedFinancialState();
        int inspected = outcomes.inspections.size();
        currentAdmin.denial = new ApplicationException(TestAccessError.EXPIRED);
        assertThatThrownBy(() -> service.allocate(input)).isInstanceOf(ApplicationException.class);
        assertThat(outcomes.inspections).hasSize(inspected);
        currentAdmin.denial = null;
        assertThat(service.allocate(input)).isEqualTo(new AllocationResult.Replay(original));
        assertThat(outcomes.recordings).hasSize(1);
        assertThat(pairProbe.allocations).hasValue(1);
        assertThat(committedFinancialState()).isEqualTo(committed);
        assertAllocationFacts(1);
    }

    private AllocationJdbcCommitProbe.Trace onlyTrace() {
        assertThat(commits.transactions).hasSize(1);
        return commits.transactions.getFirst();
    }

    private static Throwable deepest(Throwable error) {
        while (error.getCause() != null) { error = error.getCause(); }
        return error;
    }

    private enum TestAccessError implements ErrorCode {
        EXPIRED;
        @Override public String code() { return "TEST_SESSION_EXPIRED"; }
        @Override public String publicMessage() { return "Authentication required"; }
        @Override public Category category() { return Category.FORBIDDEN; }
    }
}
