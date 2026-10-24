package com.finfast.ledger.internal.application.usecase;

import com.finfast.accounts.api.AccountIdentityQueries;
import com.finfast.accounts.api.AccountIdentityQueries.Identity;
import com.finfast.accounts.api.AccountIdentityQueries.Kind;
import com.finfast.accounts.api.AccountNumber;
import com.finfast.ledger.api.LedgerQueries.Direction;
import com.finfast.ledger.api.LedgerQueries.Movement;
import com.finfast.ledger.api.LedgerQueries.Page;
import com.finfast.ledger.api.LedgerQueries.PageRequest;
import com.finfast.ledger.api.LedgerQueries.Type;
import com.finfast.ledger.internal.application.dto.HistoryCursor;
import com.finfast.ledger.internal.application.port.inbound.ReadCustomerHistory;
import com.finfast.ledger.internal.application.port.outbound.CustomerHistoryFacts;
import com.finfast.ledger.internal.application.service.HistoryCursorCodec;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Ledger-local History contribution; History authorizes current ownership before every call.
 * Explicit proxy composition must share one snapshot with Accounts AM-02 and inject a stable cursor key.
 * No incomplete LedgerQueries provider or production registration while BK-015/real dependencies are absent.
 */
public class CustomerHistoryQueryService implements ReadCustomerHistory {
    private final CustomerHistoryFacts facts;
    private final AccountIdentityQueries accounts;
    private final HistoryCursorCodec cursors;

    public CustomerHistoryQueryService(CustomerHistoryFacts facts, AccountIdentityQueries accounts, HistoryCursorCodec cursors) {
        this.facts = Objects.requireNonNull(facts, "facts");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true,
            isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public Page history(UUID participantAccountId, PageRequest request) {
        Objects.requireNonNull(participantAccountId, "participantAccountId");
        Objects.requireNonNull(request, "request");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                    .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new IllegalTransactionStateException("History requires a read-only REPEATABLE READ transaction");
        }
        var continuation = request.cursor().map(cursors::decode);
        continuation.ifPresent(cursor -> {
            if (!cursor.participantAccountId().equals(participantAccountId) || cursor.limit() != request.limit()) {
                throw invalidCursor();
            }
        });
        Map<UUID, Identity> identities = new HashMap<>();
        Map<AccountNumber, UUID> numbers = new HashMap<>();
        Map<UUID, UUID> owners = new HashMap<>();
        Identity participant = resolve(participantAccountId, identities, numbers, owners);
        if (participant.kind() != Kind.CUSTOMER) {
            throw new IllegalStateException("Customer history requires a Customer Account identity");
        }
        UUID owner = participant.customerId().orElseThrow();
        continuation.ifPresent(cursor -> {
            if (!cursor.ownerCustomerId().equals(owner)) {
                throw invalidCursor();
            }
        });
        long highWater = continuation.isPresent() ? continuation.get().highWater() : facts.highWater();
        OptionalLong before = continuation.isPresent() ? OptionalLong.of(continuation.get().lastSeen()) : OptionalLong.empty();
        var transactions = facts.page(participantAccountId, highWater, before, request.limit() + 1);
        var movements = new ArrayList<Movement>(transactions.size());
        for (var transaction : transactions) {
            var entry = transaction.entries().stream().filter(e -> e.accountId().equals(participantAccountId)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("History facts omit the requested participant"));
            var counterpartyEntry = transaction.entries().stream().filter(e -> !e.accountId().equals(participantAccountId)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("History facts omit the counterparty"));
            Identity counterparty = resolve(counterpartyEntry.accountId(), identities, numbers, owners);
            Type type = switch (transaction.type()) {
                case INTERNAL_TRANSFER -> Type.INTERNAL_TRANSFER;
                case DEMO_ALLOCATION -> Type.DEMO_ALLOCATION;
                case GENESIS -> throw new IllegalStateException("GENESIS cannot be a Customer movement");
            };
            if ((type == Type.INTERNAL_TRANSFER && counterparty.kind() != Kind.CUSTOMER)
                    || (type == Type.DEMO_ALLOCATION && (entry.deltaVnd() < 0 || counterparty.kind() != Kind.TREASURY))) {
                throw new IllegalStateException("Account kinds contradict committed Customer movement");
            }
            movements.add(new Movement(transaction.reference(), type,
                    entry.deltaVnd() < 0 ? Direction.OUTGOING : Direction.INCOMING, transaction.amount(),
                    counterparty.kind() == Kind.TREASURY ? "Treasury" : counterparty.number().value(), transaction.recordedAt()));
        }
        boolean more = movements.size() > request.limit();
        Optional<String> next = more ? Optional.of(cursors.encode(new HistoryCursor(owner, participantAccountId, highWater,
                transactions.get(request.limit() - 1).commitSeq(), request.limit()))) : Optional.empty();
        return new Page(movements.subList(0, Math.min(movements.size(), request.limit())), next);
    }

    private Identity resolve(UUID accountId, Map<UUID, Identity> identities, Map<AccountNumber, UUID> numbers, Map<UUID, UUID> owners) {
        return identities.computeIfAbsent(accountId, id -> {
            Identity identity = accounts.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Missing Account identity for committed history"));
            if (!id.equals(identity.accountId())) {
                throw new IllegalStateException("History Account identity does not match the requested Account");
            }
            UUID previousNumber = numbers.putIfAbsent(identity.number(), id);
            UUID previousOwner = identity.customerId().map(customer -> owners.putIfAbsent(customer, id)).orElse(null);
            if ((previousNumber != null && !previousNumber.equals(id)) || (previousOwner != null && !previousOwner.equals(id))) {
                throw new IllegalStateException("Inconsistent Account Number or Customer ownership in committed history");
            }
            return identity;
        });
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("Invalid history cursor");
    }
}
