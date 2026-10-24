package com.finfast.ledger.internal.application.usecase;

import com.finfast.accounts.api.PostingAccounts;
import com.finfast.common.money.VndArithmetic;
import com.finfast.ledger.api.FinancialPosting;
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

/** Transfers-only Ledger contribution. Explicit composition needs real Accounts and intercepted transaction proxies. */
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public class TransferPostingService implements FinancialPosting {

    private final PostingAccounts accounts;
    private final PostingPositionAllocator positions;
    private final FinancialTransactionStore facts;
    private final Clock clock;

    public TransferPostingService(PostingAccounts accounts, PostingPositionAllocator positions,
                                   FinancialTransactionStore facts, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.positions = Objects.requireNonNull(positions, "positions");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PostingResult postTransfer(Intent intent) {
        Objects.requireNonNull(intent, "intent");
        if (intent.amount().value() == 0) {
            return new PostingResult.Rejected(LedgerPostingError.INVALID_AMOUNT);
        }
        // Trusted source parameters come from Transfers; Accounts owns locks and current-state eligibility.
        PostingAccounts.Decision decision = Objects.requireNonNull(accounts.transfer(intent.sourceAccountId(),
                intent.sourceCustomerId(), intent.recipientNumber(), intent.amount()), "Accounts decision");
        if (decision instanceof PostingAccounts.Rejected rejected) {
            return new PostingResult.Rejected(rejected.reason());
        }
        PostingAccounts.Applied applied = (PostingAccounts.Applied) decision;
        long position = positions.reserveNext(); // After the complete participant set has been locked and applied.
        UUID id = UUID.randomUUID();
        FinancialTransaction transaction = FinancialTransaction.internalTransfer(id, "TRANSFER-" + id, intent.amount(),
                position, clock.instant().truncatedTo(ChronoUnit.MICROS), List.of(
                        new FinancialTransaction.Entry(UUID.randomUUID(), applied.debitAccountId(),
                                VndArithmetic.negateExact(intent.amount().value())),
                        new FinancialTransaction.Entry(UUID.randomUUID(), applied.creditAccountId(), intent.amount().value())));
        facts.insert(transaction);
        // Note stays unchanged in the caller's intent/outcome; the frozen schema has no Ledger note column.
        // Final outcome and commit confirmation still belong to the outer Transfers transaction.
        return new PostingResult.Pending(transaction.id(), transaction.reference(), transaction.amount(), transaction.recordedAt());
    }
}
