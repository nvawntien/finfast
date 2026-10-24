package com.finfast.administration.internal.application.usecase;

import com.finfast.accounts.api.AccountQueries;
import com.finfast.administration.internal.application.dto.AllocationCommand;
import com.finfast.administration.internal.application.dto.AllocationResult;
import com.finfast.administration.internal.application.port.inbound.AllocateDemoFunds;
import com.finfast.administration.internal.application.port.outbound.AdminAuditStore;
import com.finfast.administration.internal.application.service.AllocationCanonicalIntent;
import com.finfast.administration.internal.domain.entity.AdminAuditEvent;
import com.finfast.common.error.ErrorCode;
import com.finfast.idempotency.api.RequestOutcomes;
import com.finfast.identity.api.CurrentIdentity;
import com.finfast.ledger.api.AllocationPosting;
import com.finfast.ledger.api.PostingResult;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Administration's outer allocation transaction. Explicit proxy composition must supply all participants
 * on one PostgreSQL DataSource/transaction manager. No automatic Bean or missing-provider substitute.
 * Composition must enable rollbackOnCommitFailure for pre-commit TransactionException callbacks;
 * ambiguous commit/connection failures still require original-key recovery, never a rejection.
 * Technical failures escape; a fresh retry belongs outside the outermost proxy, using the original key.
 */
public class AllocateDemoFundsService implements AllocateDemoFunds {
    private final CurrentIdentity identity;
    private final RequestOutcomes outcomes;
    private final AccountQueries accounts;
    private final AllocationPosting posting;
    private final AdminAuditStore audits;
    private final Clock clock;

    public AllocateDemoFundsService(CurrentIdentity identity, RequestOutcomes outcomes, AccountQueries accounts,
                                    AllocationPosting posting, AdminAuditStore audits, Clock clock) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.outcomes = Objects.requireNonNull(outcomes, "outcomes");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.posting = Objects.requireNonNull(posting, "posting");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AllocationResult allocate(AllocationCommand command) {
        requireWriteTransaction();
        var admin = Objects.requireNonNull(identity.requireAdmin(), "trusted Admin");
        Objects.requireNonNull(command, "command");
        var scope = new RequestOutcomes.Scope(admin.userId(), RequestOutcomes.Operation.DEMO_ALLOCATION, command.key());
        var canonical = AllocationCanonicalIntent.create(admin.userId(), command);
        var decision = Objects.requireNonNull(outcomes.inspect(scope, canonical), "Idempotency decision");
        return switch (decision) {
            case RequestOutcomes.Replay replay -> new AllocationResult.Replay(replay.outcome());
            case RequestOutcomes.Conflict ignored -> new AllocationResult.Conflict();
            case RequestOutcomes.NotFinal state -> state == RequestOutcomes.NotFinal.PROCESSING
                    ? AllocationResult.NotFinal.PROCESSING : AllocationResult.NotFinal.UNKNOWN;
            case RequestOutcomes.Execute ignored -> execute(admin.userId(), command, scope, canonical);
        };
    }

    private AllocationResult execute(UUID adminId, AllocationCommand command, RequestOutcomes.Scope scope,
                                     RequestOutcomes.CanonicalIntent canonical) {
        if (command.amount().value() == 0) {
            return reject(scope, canonical, AllocationError.INVALID_AMOUNT);
        }
        if (command.reason().isBlank()) {
            return reject(scope, canonical, AllocationError.REASON_REQUIRED);
        }
        var recipient = accounts.findCustomerByNumber(command.recipientNumber());
        if (recipient.isEmpty()) {
            return reject(scope, canonical, AllocationError.RECIPIENT_NOT_FOUND);
        }
        if (!recipient.get().number().equals(command.recipientNumber())) {
            throw new IllegalStateException("Account identity does not match the requested recipient");
        }
        // This snapshot supplies immutable audit identity only. Ledger/Accounts revalidate locked eligibility.
        var result = Objects.requireNonNull(posting.postAllocation(new AllocationPosting.Intent(
                adminId, command.recipientNumber(), command.amount(), command.reason())), "Ledger result");
        if (result instanceof PostingResult.Rejected rejected) {
            return reject(scope, canonical, rejected.reason());
        }
        var pending = (PostingResult.Pending) result;
        if (!pending.amount().equals(command.amount())) {
            throw new IllegalStateException("Posting amount does not match the requested allocation");
        }
        audits.insert(new AdminAuditEvent(UUID.randomUUID(), adminId, recipient.get().accountId(), command.reason(),
                pending.transactionId(), clock.instant()));
        var completion = new RequestOutcomes.Completion(pending.transactionId(), pending.reference(),
                pending.amount(), pending.recordedAt());
        outcomes.record(scope, canonical, completion);
        return new AllocationResult.Pending(completion);
    }

    private AllocationResult reject(RequestOutcomes.Scope scope, RequestOutcomes.CanonicalIntent canonical, ErrorCode error) {
        var rejection = new RequestOutcomes.Rejection(error.code(), error.publicMessage());
        outcomes.record(scope, canonical, rejection);
        return new AllocationResult.Pending(rejection);
    }

    private static void requireWriteTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_READ_COMMITTED)
                    .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new IllegalTransactionStateException("Allocation requires an intercepted READ COMMITTED write transaction");
        }
    }
}
