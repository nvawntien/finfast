package com.finfast.ledger.internal.application.usecase;

import com.finfast.accounts.api.AccountIdentityQueries;
import com.finfast.accounts.api.AccountIdentityQueries.Identity;
import com.finfast.accounts.api.AccountIdentityQueries.Kind;
import com.finfast.accounts.api.AccountNumber;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;
import com.finfast.ledger.internal.application.port.outbound.CommittedFinancialTransactions;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
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
import java.util.UUID;

/**
 * AM-04 projection; Administration authorizes the caller. Explicit composition must invoke this
 * through its Spring proxy and join Accounts identity reads to the same DataSource/transaction manager.
 * No production registration until the real AM-02 provider is available.
 */
public class AdminFinancialTransactionQueryService implements AdminFinancialTransactionQueries {
    private final CommittedFinancialTransactions facts;
    private final AccountIdentityQueries accounts;

    public AdminFinancialTransactionQueryService(CommittedFinancialTransactions facts, AccountIdentityQueries accounts) {
        this.facts = Objects.requireNonNull(facts, "facts");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true,
            isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public Transactions listCommitted() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                    .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new IllegalTransactionStateException("Financial inspection requires a read-only REPEATABLE READ transaction");
        }
        // The port also verifies the physical bound resource before any facts or identities are read.
        var committed = facts.listCommitted();
        Map<UUID, Identity> identities = new HashMap<>();
        Map<AccountNumber, UUID> numbers = new HashMap<>();
        var transactions = new ArrayList<Transaction>(committed.size());
        for (var fact : committed) {
            var entries = new ArrayList<Entry>(fact.entries().size());
            for (var entry : fact.entries()) {
                Identity identity = identities.computeIfAbsent(entry.accountId(), id -> resolve(id, numbers));
                Kind expected = fact.type() == FinancialTransaction.Type.GENESIS
                        || (fact.type() == FinancialTransaction.Type.DEMO_ALLOCATION && entry.deltaVnd() < 0)
                        ? Kind.TREASURY : Kind.CUSTOMER;
                if (identity.kind() != expected) {
                    throw new IllegalStateException("Account kind contradicts committed Ledger facts");
                }
                entries.add(new Entry(identity.number(), entry.deltaVnd()));
            }
            Type type = switch (fact.type()) {
                case GENESIS -> Type.GENESIS;
                case INTERNAL_TRANSFER -> Type.INTERNAL_TRANSFER;
                case DEMO_ALLOCATION -> Type.DEMO_ALLOCATION;
            };
            transactions.add(new Transaction(fact.id(), fact.reference(), type, fact.amount(), fact.recordedAt(), entries));
        }
        return new Transactions(transactions);
    }

    private Identity resolve(UUID accountId, Map<AccountNumber, UUID> numbers) {
        Identity identity = accounts.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Missing Account identity for committed Ledger facts"));
        if (!accountId.equals(identity.accountId())) {
            throw new IllegalStateException("Account identity does not match the committed participant");
        }
        UUID previous = numbers.putIfAbsent(identity.number(), accountId);
        if (previous != null && !previous.equals(accountId)) {
            throw new IllegalStateException("Distinct committed participants share an Account Number");
        }
        return identity;
    }
}
