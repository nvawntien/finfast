package com.finfast.ledger.internal.application.usecase;

import com.finfast.accounts.api.PostingAccounts;
import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.GenesisPosting;
import com.finfast.ledger.api.PostingResult;
import com.finfast.ledger.internal.application.port.outbound.FinancialTransactionStore;
import com.finfast.ledger.internal.application.port.outbound.GenesisDataset;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ledger's contribution to protected Bootstrap setup, never an ordinary posting or startup initializer.
 * Explicit production composition requires the real Accounts provider and intercepted transaction proxies.
 * Bootstrap owns provisioning, the outer transaction, and fresh-transaction verification after a setup race.
 */
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public class GenesisPostingService implements GenesisPosting {

    private final PostingAccounts accounts;
    private final FinancialTransactionStore facts;
    private final GenesisDataset dataset;
    private final Clock clock;

    public GenesisPostingService(PostingAccounts accounts, FinancialTransactionStore facts,
                                 GenesisDataset dataset, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.dataset = Objects.requireNonNull(dataset, "dataset");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PostingResult.Pending initialize(UUID treasuryAccountId, VndAmount initialSupply) {
        Objects.requireNonNull(treasuryAccountId, "treasuryAccountId");
        Objects.requireNonNull(initialSupply, "initialSupply").requirePositive();
        // Also checks the actual bound JDBC resource, isolation and read-only state before Accounts is invoked.
        // This is an early rejection, not an absent-row lock; uniqueness and outer rollback protect races.
        dataset.requireUninitialized();
        accounts.initializeTreasury(treasuryAccountId, initialSupply);

        UUID transactionId = UUID.randomUUID();
        FinancialTransaction genesis = FinancialTransaction.genesis(transactionId, "GENESIS-" + transactionId,
                initialSupply, 1, clock.instant().truncatedTo(ChronoUnit.MICROS), treasuryAccountId,
                List.of(new FinancialTransaction.Entry(UUID.randomUUID(), treasuryAccountId, initialSupply.value())));
        facts.insert(genesis);
        dataset.insertGenesis(genesis);
        // No commit has happened here. Every effect must still succeed and commit in Bootstrap's outer transaction.
        return new PostingResult.Pending(genesis.id(), genesis.reference(), genesis.amount(), genesis.recordedAt());
    }
}
