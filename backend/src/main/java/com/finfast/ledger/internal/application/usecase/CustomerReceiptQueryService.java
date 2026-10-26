package com.finfast.ledger.internal.application.usecase;

import com.finfast.accounts.api.AccountIdentityQueries;
import com.finfast.accounts.api.AccountIdentityQueries.Identity;
import com.finfast.accounts.api.AccountIdentityQueries.Kind;
import com.finfast.ledger.api.LedgerQueries.Receipt;
import com.finfast.ledger.internal.application.port.inbound.ReadCustomerReceipt;
import com.finfast.ledger.internal.application.port.outbound.CustomerReceiptFacts;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** BK-015 immutable receipt projection for an already-authorized participant. */
public class CustomerReceiptQueryService implements ReadCustomerReceipt {
    private final CustomerReceiptFacts facts;
    private final AccountIdentityQueries accounts;

    public CustomerReceiptQueryService(CustomerReceiptFacts facts, AccountIdentityQueries accounts) {
        this.facts = Objects.requireNonNull(facts, "facts");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true,
            isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public Optional<Receipt> receipt(UUID participantAccountId, String reference) {
        Objects.requireNonNull(participantAccountId, "participantAccountId");
        Objects.requireNonNull(reference, "reference");
        requireReadSnapshot();

        Optional<Identity> participantResult = accounts.findById(participantAccountId);
        if (participantResult.isEmpty()) {
            return Optional.empty();
        }
        Identity participant = validateIdentity(participantAccountId, participantResult.get(), "participant");
        if (participant.kind() != Kind.CUSTOMER || participant.customerId().isEmpty()) {
            return Optional.empty();
        }
        Optional<FinancialTransaction> fact = facts.find(participantAccountId, reference);
        if (fact.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toReceipt(participantAccountId, reference, participant, fact.get()));
    }

    private Receipt toReceipt(UUID participantAccountId, String requestedReference,
                              Identity participant, FinancialTransaction fact) {
        if (fact.type() == FinancialTransaction.Type.GENESIS || fact.entries().size() != 2) {
            throw new IllegalStateException("Customer receipt requires one complete ordinary movement");
        }
        if (!fact.reference().equals(requestedReference)) {
            throw new IllegalStateException("Receipt facts reference does not match the requested reference");
        }
        var participantEntry = fact.entries().stream()
                .filter(entry -> entry.accountId().equals(participantAccountId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Receipt facts omit the requested participant"));
        var otherEntry = fact.entries().stream()
                .filter(entry -> !entry.accountId().equals(participantAccountId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Receipt facts omit the counterparty"));
        Identity counterparty = accounts.findById(otherEntry.accountId())
                .map(identity -> validateIdentity(otherEntry.accountId(), identity, "counterparty"))
                .orElseThrow(() -> new IllegalStateException("Missing counterparty Account identity"));
        validateKinds(fact.type(), participantEntry.deltaVnd(), participant, counterparty);

        Identity source = participantEntry.deltaVnd() < 0 ? participant : counterparty;
        Identity destination = participantEntry.deltaVnd() < 0 ? counterparty : participant;
        return new Receipt(fact.reference(), switch (fact.type()) {
            case INTERNAL_TRANSFER -> com.finfast.ledger.api.LedgerQueries.Type.INTERNAL_TRANSFER;
            case DEMO_ALLOCATION -> com.finfast.ledger.api.LedgerQueries.Type.DEMO_ALLOCATION;
            case GENESIS -> throw new IllegalStateException("GENESIS cannot be a Customer receipt");
        }, fact.amount(), label(source), label(destination), fact.recordedAt());
    }

    private static Identity validateIdentity(UUID requestedAccountId, Identity identity, String role) {
        if (!requestedAccountId.equals(identity.accountId())) {
            throw new IllegalStateException("Receipt " + role + " identity does not match the requested Account");
        }
        return identity;
    }

    private static void validateKinds(FinancialTransaction.Type type, long participantDelta,
                                      Identity participant, Identity counterparty) {
        if (participant.kind() != Kind.CUSTOMER || participant.customerId().isEmpty()) {
            throw new IllegalStateException("Receipt participant is not a Customer Account");
        }
        if (type == FinancialTransaction.Type.INTERNAL_TRANSFER) {
            if (counterparty.kind() != Kind.CUSTOMER || counterparty.customerId().isEmpty()) {
                throw new IllegalStateException("Transfer receipt counterparty is not a Customer Account");
            }
            return;
        }
        if (type != FinancialTransaction.Type.DEMO_ALLOCATION
                || participantDelta <= 0 || counterparty.kind() != Kind.TREASURY
                || counterparty.customerId().isPresent()) {
            throw new IllegalStateException("Allocation receipt has incompatible Account identities");
        }
    }

    private static String label(Identity identity) {
        return identity.kind() == Kind.TREASURY ? "Treasury" : identity.number().value();
    }

    private static void requireReadSnapshot() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                    .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new IllegalTransactionStateException("Receipt requires a read-only REPEATABLE READ transaction");
        }
    }
}
