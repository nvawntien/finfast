package com.finfast.administration.internal.application.usecase;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.administration.internal.application.dto.AllocationCommand;
import com.finfast.administration.internal.application.dto.AllocationResult;
import com.finfast.administration.internal.application.port.inbound.AllocateDemoFunds;
import com.finfast.administration.internal.application.port.outbound.AdminAuditStore;
import com.finfast.administration.internal.application.service.AllocationCanonicalIntent;
import com.finfast.administration.internal.domain.entity.AdminAuditEvent;
import com.finfast.common.error.ApplicationException;
import com.finfast.common.error.ErrorCode;
import com.finfast.common.money.VndAmount;
import com.finfast.idempotency.api.RequestOutcomes;
import com.finfast.identity.api.CurrentIdentity;
import com.finfast.ledger.api.AllocationPosting;
import com.finfast.ledger.api.PostingResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Consumer control-flow evidence only. Transaction flags/mocks here prove no database behavior. */
class AllocateDemoFundsServiceTests {
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00.123456789Z");
    private final CurrentIdentity identity = mock(CurrentIdentity.class);
    private final RequestOutcomes outcomes = mock(RequestOutcomes.class);
    private final AccountQueries accounts = mock(AccountQueries.class);
    private final AllocationPosting posting = mock(AllocationPosting.class);
    private final AdminAuditStore audits = mock(AdminAuditStore.class);
    private final AllocationCommand command = new AllocationCommand(new RequestOutcomes.Key(" Exact key 😀 "),
            new AccountNumber("FF-1"), new VndAmount(25), "  reason 😀 e\u0301  ");
    private final PostingResult.Pending pending = new PostingResult.Pending(UUID.randomUUID(), "original-reference",
            command.amount(), NOW.minusSeconds(1));
    private AllocateDemoFunds service;

    @BeforeEach
    void arrangeConsumerCollaborators() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        service = new AllocateDemoFundsService(identity, outcomes, accounts, posting, audits, Clock.fixed(NOW, ZoneOffset.UTC));
        when(identity.requireAdmin()).thenReturn(new CurrentIdentity.AdminActor(ADMIN));
        when(outcomes.inspect(any(), any())).thenReturn(new RequestOutcomes.Execute());
        when(accounts.findCustomerByNumber(command.recipientNumber())).thenReturn(Optional.of(snapshot(CustomerAccount.Status.ACTIVE)));
        when(posting.postAllocation(any())).thenReturn(pending);
    }

    @AfterEach
    void clearConsumerTransactionFlags() {
        TransactionSynchronizationManager.clear();
    }

    @ParameterizedTest
    @EnumSource(value = Denial.class, names = {"MISSING", "EXPIRED", "CUSTOMER"})
    void absentExpiredAndWrongRoleIdentityStopBeforeProtectedOperations(Denial denial) {
        var error = new ApplicationException(denial);
        when(identity.requireAdmin()).thenThrow(error);
        assertThatThrownBy(() -> service.allocate(command)).isSameAs(error);
        verifyNoInteractions(outcomes, accounts, posting, audits);
    }

    @Test
    void executeUsesTrustedActorExactScopeAndOnePostingAuditAndOriginalCompletionInOrder() {
        var result = service.allocate(command);
        var scope = new RequestOutcomes.Scope(ADMIN, RequestOutcomes.Operation.DEMO_ALLOCATION, command.key());
        var canonical = AllocationCanonicalIntent.create(ADMIN, command);
        var completion = new RequestOutcomes.Completion(pending.transactionId(), pending.reference(), pending.amount(), pending.recordedAt());
        assertThat(result).isEqualTo(new AllocationResult.Pending(completion));
        var audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        var order = inOrder(identity, outcomes, accounts, posting, audits);
        order.verify(identity).requireAdmin();
        order.verify(outcomes).inspect(scope, canonical);
        order.verify(accounts).findCustomerByNumber(command.recipientNumber());
        order.verify(posting).postAllocation(new AllocationPosting.Intent(ADMIN, command.recipientNumber(), command.amount(), command.reason()));
        order.verify(audits).insert(audit.capture());
        order.verify(outcomes).record(scope, canonical, completion);
        assertThat(audit.getValue()).isEqualTo(new AdminAuditEvent(audit.getValue().id(), ADMIN, TARGET,
                command.reason(), pending.transactionId(), NOW));
        verifyNoMoreInteractions(identity, outcomes, accounts, posting, audits);
    }

    @Test
    void latestTrustedIdentityDefinesScopeAndPostingActor() {
        UUID anotherAdmin = UUID.randomUUID();
        when(identity.requireAdmin()).thenReturn(new CurrentIdentity.AdminActor(anotherAdmin));
        service.allocate(command);
        verify(outcomes).inspect(new RequestOutcomes.Scope(anotherAdmin, RequestOutcomes.Operation.DEMO_ALLOCATION, command.key()),
                AllocationCanonicalIntent.create(anotherAdmin, command));
        verify(posting).postAllocation(new AllocationPosting.Intent(anotherAdmin, command.recipientNumber(), command.amount(), command.reason()));
        var audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(audits).insert(audit.capture());
        assertThat(audit.getValue().adminUserId()).isEqualTo(anotherAdmin);
    }

    @Test
    void finalizedRejectionReplaysBeforeReevaluatingZeroAmountOrBlankReason() {
        var original = new RequestOutcomes.Rejection("INVALID_AMOUNT", "Amount must be positive");
        when(outcomes.inspect(any(), any())).thenReturn(new RequestOutcomes.Replay(original));
        var rejectedCommand = new AllocationCommand(command.key(), command.recipientNumber(), new VndAmount(0), " ");
        assertThat(service.allocate(rejectedCommand)).isEqualTo(new AllocationResult.Replay(original));
        verify(outcomes, never()).record(any(), any(), any());
        verifyNoInteractions(accounts, posting, audits);
    }

    @ParameterizedTest
    @MethodSource("originalOutcomes")
    void replayReturnsExactOriginalWithoutRecipientLookupOrNewWrites(RequestOutcomes.FinalOutcome original) {
        when(outcomes.inspect(any(), any())).thenReturn(new RequestOutcomes.Replay(original));
        assertThat(service.allocate(command)).isEqualTo(new AllocationResult.Replay(original));
        verify(outcomes, never()).record(any(), any(), any());
        verifyNoInteractions(accounts, posting, audits);
    }

    static Stream<RequestOutcomes.FinalOutcome> originalOutcomes() {
        return Stream.of(new RequestOutcomes.Completion(UUID.randomUUID(), "original", new VndAmount(25), NOW),
                new RequestOutcomes.Rejection("FROZEN", "Account is frozen"));
    }

    @Test
    void conflictNeverChangesOriginalOutcomeOrPerformsFunding() {
        when(outcomes.inspect(any(), any())).thenReturn(new RequestOutcomes.Conflict());
        assertThat(service.allocate(command)).isEqualTo(new AllocationResult.Conflict());
        verify(outcomes, never()).record(any(), any(), any());
        verifyNoInteractions(accounts, posting, audits);
    }

    @ParameterizedTest
    @EnumSource(RequestOutcomes.NotFinal.class)
    void processingAndUnknownNeverAuthorizeNewExecution(RequestOutcomes.NotFinal state) {
        when(outcomes.inspect(any(), any())).thenReturn(state);
        assertThat(service.allocate(command)).isEqualTo(AllocationResult.NotFinal.valueOf(state.name()));
        verify(outcomes, never()).record(any(), any(), any());
        verifyNoInteractions(accounts, posting, audits);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\r\n", "\u2003"})
    void blankReasonIsFinalizedAfterInspectionWithoutPosting(String reason) {
        assertRejection(new AllocationCommand(command.key(), command.recipientNumber(), command.amount(), reason), "REASON_REQUIRED");
        verifyNoInteractions(accounts, posting, audits);
    }

    @Test
    void zeroAmountIsFinalizedWithoutPosting() {
        assertRejection(new AllocationCommand(command.key(), command.recipientNumber(), new VndAmount(0), "r"), "INVALID_AMOUNT");
        verifyNoInteractions(accounts, posting, audits);
    }

    @Test
    void missingRecipientIsFinalizedWithoutFabricatingAuditIdentity() {
        when(accounts.findCustomerByNumber(any())).thenReturn(Optional.empty());
        assertRejection(command, "RECIPIENT_NOT_FOUND");
        verifyNoInteractions(posting, audits);
    }

    @Test
    void frozenReadSnapshotCannotOverrideCurrentPostingDecision() {
        when(accounts.findCustomerByNumber(any())).thenReturn(Optional.of(snapshot(CustomerAccount.Status.FROZEN)));
        assertThat(service.allocate(command)).isInstanceOf(AllocationResult.Pending.class);
        verify(posting).postAllocation(any());
        verify(audits).insert(any());
    }

    @Test
    void activeReadSnapshotCannotOverridePostingRejection() {
        when(posting.postAllocation(any())).thenReturn(new PostingResult.Rejected(Denial.FROZEN));
        assertRejection(command, "FROZEN");
        verifyNoInteractions(audits);
    }

    @Test
    void insufficientTreasuryResultPassesThroughAsSafeFinalRejection() {
        when(posting.postAllocation(any())).thenReturn(new PostingResult.Rejected(Denial.INSUFFICIENT_FUNDS));
        assertRejection(command, "INSUFFICIENT_FUNDS");
        verifyNoInteractions(audits);
    }

    @ParameterizedTest
    @ValueSource(strings = {"inspect", "recipient", "posting", "audit", "outcome"})
    void technicalFailuresEscapeWithoutBecomingDurableRejections(String boundary) {
        var error = new IllegalStateException("synthetic technical failure");
        switch (boundary) {
            case "inspect" -> when(outcomes.inspect(any(), any())).thenThrow(error);
            case "recipient" -> when(accounts.findCustomerByNumber(any())).thenThrow(error);
            case "posting" -> when(posting.postAllocation(any())).thenThrow(error);
            case "audit" -> doThrow(error).when(audits).insert(any());
            case "outcome" -> doThrow(error).when(outcomes).record(any(), any(), any());
            default -> throw new AssertionError(boundary);
        }
        assertThatThrownBy(() -> service.allocate(command)).isSameAs(error);
        verify(outcomes, never()).record(any(), any(), any(RequestOutcomes.Rejection.class));
        if (!boundary.equals("outcome")) {
            verify(outcomes, never()).record(any(), any(), any());
        }
    }

    @Test
    void mismatchedRecipientIdentityFailsBeforePosting() {
        when(accounts.findCustomerByNumber(any())).thenReturn(Optional.of(new CustomerAccount(TARGET, UUID.randomUUID(),
                new AccountNumber("different"), CustomerAccount.Status.ACTIVE, new VndAmount(0))));
        assertThatThrownBy(() -> service.allocate(command)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(posting, audits);
        verify(outcomes, never()).record(any(), any(), any());
    }

    @Test
    void inconsistentPostingAmountIsTechnicalAndCannotBeRecordedAsSuccess() {
        when(posting.postAllocation(any())).thenReturn(new PostingResult.Pending(UUID.randomUUID(), "ref", new VndAmount(99), NOW));
        assertThatThrownBy(() -> service.allocate(command)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(audits);
        verify(outcomes, never()).record(any(), any(), any());
    }

    @Test
    void unproxiedCallWithoutTransactionFailsBeforeAnyProvider() {
        TransactionSynchronizationManager.clear();
        assertThatThrownBy(() -> service.allocate(command)).isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(identity, outcomes, accounts, posting, audits);
    }

    private void assertRejection(AllocationCommand input, String code) {
        var result = (AllocationResult.Pending) service.allocate(input);
        var rejection = (RequestOutcomes.Rejection) result.outcome();
        assertThat(rejection.code()).isEqualTo(code);
        verify(outcomes).record(eq(new RequestOutcomes.Scope(ADMIN, RequestOutcomes.Operation.DEMO_ALLOCATION, input.key())),
                eq(AllocationCanonicalIntent.create(ADMIN, input)), eq(rejection));
    }

    private CustomerAccount snapshot(CustomerAccount.Status status) {
        return new CustomerAccount(TARGET, UUID.randomUUID(), command.recipientNumber(), status, new VndAmount(0));
    }

    enum Denial implements ErrorCode {
        MISSING, EXPIRED, CUSTOMER, FROZEN, INSUFFICIENT_FUNDS;
        @Override public String code() { return name(); }
        @Override public String publicMessage() { return "Operation is not permitted"; }
        @Override public Category category() { return Category.FORBIDDEN; }
    }
}
