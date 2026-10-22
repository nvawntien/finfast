package com.finfast.ledger.internal.application.usecase;

import com.finfast.accounts.api.PostingAccounts;
import com.finfast.common.money.VndArithmetic;
import com.finfast.ledger.api.AllocationPosting;
import com.finfast.ledger.api.PostingResult;
import com.finfast.ledger.internal.application.port.outbound.FinancialTransactionStore;
import com.finfast.ledger.internal.application.port.outbound.PostingPositionAllocator;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Administration-only Ledger contribution; no audit, durable outcome, source selection or independent commit. */
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public class AllocationPostingService implements AllocationPosting {

    private final PostingAccounts accounts;
    private final PostingPositionAllocator positions;
    private final FinancialTransactionStore facts;
    private final Clock clock;

    public AllocationPostingService(PostingAccounts accounts, PostingPositionAllocator positions,
                                     FinancialTransactionStore facts, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.positions = Objects.requireNonNull(positions, "positions");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PostingResult postAllocation(Intent intent) {
        Objects.requireNonNull(intent, "intent");
        if (intent.reason().isBlank()) {
            return new PostingResult.Rejected(LedgerPostingError.REASON_REQUIRED);
        }
        if (intent.amount().value() == 0) {
            return new PostingResult.Rejected(LedgerPostingError.INVALID_AMOUNT);
        }
        // Accounts selects Treasury and locks/revalidates both participants; no caller-selected debit UUID.
        PostingAccounts.Decision decision = Objects.requireNonNull(
                accounts.allocate(intent.recipientNumber(), intent.amount()), "Accounts decision");
        if (decision instanceof PostingAccounts.Rejected rejected) {
            return new PostingResult.Rejected(rejected.reason());
        }
        PostingAccounts.Applied applied = (PostingAccounts.Applied) decision;
        long position = positions.reserveNext();
        UUID id = UUID.randomUUID();
        FinancialTransaction transaction = FinancialTransaction.demoAllocation(id, "ALLOCATION-" + id, intent.amount(),
                position, clock.instant().truncatedTo(ChronoUnit.MICROS), List.of(
                        new FinancialTransaction.Entry(UUID.randomUUID(), applied.debitAccountId(),
                                VndArithmetic.negateExact(intent.amount().value())),
                        new FinancialTransaction.Entry(UUID.randomUUID(), applied.creditAccountId(), intent.amount().value())));
        facts.insert(transaction);
        // Exact reason remains in Administration's intent, required audit and final outcome, all still pending.
        return new PostingResult.Pending(transaction.id(), transaction.reference(), transaction.amount(), transaction.recordedAt());
    }
}
