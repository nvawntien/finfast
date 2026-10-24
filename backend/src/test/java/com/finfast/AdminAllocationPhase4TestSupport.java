package com.finfast;

import com.finfast.accounts.api.AccountQueries;
import com.finfast.administration.internal.application.dto.AllocationResult;
import com.finfast.administration.internal.application.port.inbound.AllocateDemoFunds;
import com.finfast.administration.internal.application.usecase.AllocateDemoFundsService;
import com.finfast.idempotency.api.RequestOutcomes;
import com.finfast.identity.api.CurrentIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.aop.support.AopUtils;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Ledger/audit/outer proxies; TEST-ONLY identity, Accounts and scripted outcome decisions.
 * No new idempotency lock or SQL provider: the inherited afterCommit map is NOT durable coordination.
 */
abstract class AdminAllocationPhase4TestSupport extends AdminAllocationTestSupport {
    protected ScriptedOutcomes outcomes;
    protected TestAdminContext currentAdmin;
    protected Runnable beforePosting = () -> { };
    protected Runnable afterPosting = () -> { };

    @BeforeEach
    void wirePhase4ConsumerBoundary() {
        outcomes = new ScriptedOutcomes(testOutcomes);
        currentAdmin = new TestAdminContext(adminId);
        ledgerContext.registerBean("phase4Allocation", AllocateDemoFundsService.class,
                () -> new AllocateDemoFundsService(currentAdmin, outcomes, ledgerContext.getBean(AccountQueries.class),
                        intent -> {
                            beforePosting.run();
                            var result = allocations.postAllocation(intent);
                            afterPosting.run();
                            return result;
                        }, event -> {
                            auditRepository.insert(event);
                            probe.afterAudit.run();
                        }, ledgerContext.getBean(Clock.class)));
        service = ledgerContext.getBean("phase4Allocation", AllocateDemoFunds.class);
        assertThat(AopUtils.isCglibProxy(service)).isTrue();
        assertThat(((JdbcTransactionManager) manager).getDataSource()).isSameAs(ledgerJdbc.getDataSource());
        assertThat(ledgerJdbc.queryForObject("SELECT current_database()", String.class))
                .startsWith("finfast_it_").isNotEqualTo(postgres.getDatabaseName());
    }

    /** Ledger/audit facts only. Accounts writes remain the inherited SQL TEST collaborator's behavior. */
    protected void assertAllocationFacts(int count) {
        assertThat(ledgerJdbc.queryForObject(
                "SELECT count(*) FROM financial_transaction WHERE type = 'DEMO_ALLOCATION'", Long.class)).isEqualTo(count);
        assertThat(auditCount()).isEqualTo(count);
        assertThat(ledgerJdbc.queryForObject("""
                SELECT count(*) FROM financial_transaction t
                WHERE t.type = 'DEMO_ALLOCATION' AND (
                    (SELECT count(*) FROM ledger_entry e WHERE e.transaction_id = t.id) <> 2
                    OR (SELECT sum(delta_vnd) FROM ledger_entry e WHERE e.transaction_id = t.id) <> 0
                    OR (SELECT count(*) FROM admin_audit_event a WHERE a.transaction_id = t.id) <> 1)
                """, Long.class)).isZero();
        assertThat(ledgerJdbc.queryForObject("SELECT count(*) FROM request_outcome", Long.class))
                .as("No durable Idempotency provider participates in this fixture").isZero();
    }

    protected static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(8, TimeUnit.SECONDS)).as("bounded test schedule").isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test schedule interrupted", error);
        }
    }

    protected static void meet(CyclicBarrier barrier) {
        try {
            barrier.await(8, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test schedule interrupted", error);
        } catch (Exception error) {
            throw new AssertionError("Test barrier failed", error);
        }
    }

    protected static RequestOutcomes.Completion completion(AllocationResult result) {
        return (RequestOutcomes.Completion) ((AllocationResult.Pending) result).outcome();
    }

    protected record Observed(RequestOutcomes.Scope scope, RequestOutcomes.CanonicalIntent intent) { }

    /** Supplies decisions selected by each test; it does not establish exclusive execution. */
    protected static final class ScriptedOutcomes implements RequestOutcomes {
        private final RequestOutcomes delegate;
        final ConcurrentLinkedQueue<Observed> inspections = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Observed> recordings = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<FinalOutcome> attemptedOutcomes = new ConcurrentLinkedQueue<>();
        volatile BiFunction<Scope, CanonicalIntent, Decision> decision = (scope, intent) -> null;

        ScriptedOutcomes(RequestOutcomes delegate) {
            this.delegate = delegate;
        }

        @Override public Decision inspect(Scope scope, CanonicalIntent intent) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            inspections.add(new Observed(scope, intent));
            var selected = decision.apply(scope, intent);
            return selected != null ? selected : delegate.inspect(scope, intent);
        }

        @Override public void record(Scope scope, CanonicalIntent intent, FinalOutcome outcome) {
            recordings.add(new Observed(scope, intent));
            attemptedOutcomes.add(outcome);
            delegate.record(scope, intent, outcome);
        }

        @Override public Optional<FinalOutcome> find(Scope scope) {
            throw new UnsupportedOperationException("No production lookup/recovery provider in this fixture");
        }

        List<Observed> inspected() { return List.copyOf(inspections); }
    }

    /** Trusted-context simulation only; no actor field is introduced on the allocation command. */
    protected static final class TestAdminContext implements CurrentIdentity {
        private final UUID defaultAdmin;
        private final ThreadLocal<UUID> actor = new ThreadLocal<>();
        RuntimeException denial;

        TestAdminContext(UUID defaultAdmin) { this.defaultAdmin = defaultAdmin; }

        <T> T as(UUID admin, Supplier<T> work) {
            actor.set(admin);
            try { return work.get(); } finally { actor.remove(); }
        }

        @Override public AdminActor requireAdmin() {
            if (denial != null) { throw denial; }
            return new AdminActor(actor.get() == null ? defaultAdmin : actor.get());
        }

        @Override public CustomerActor requireCustomer() {
            throw new UnsupportedOperationException("Administration test boundary");
        }
    }
}
