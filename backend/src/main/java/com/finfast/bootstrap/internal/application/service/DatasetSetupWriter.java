package com.finfast.bootstrap.internal.application.service;

import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.common.money.VndAmount;
import com.finfast.identity.api.AdminProvisioning;
import com.finfast.ledger.api.GenesisPosting;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/** Bootstrap's single setup attempt. Providers must join this transaction on the same DataSource/manager. */
public class DatasetSetupWriter {
    private final AdminProvisioning admins;
    private final TreasuryProvisioning treasuries;
    private final GenesisPosting genesis;
    private final VndAmount initialSupply;

    public DatasetSetupWriter(AdminProvisioning admins, TreasuryProvisioning treasuries,
                              GenesisPosting genesis, long initialSupply) {
        this.admins = Objects.requireNonNull(admins, "admins");
        this.treasuries = Objects.requireNonNull(treasuries, "treasuries");
        this.genesis = Objects.requireNonNull(genesis, "genesis");
        this.initialSupply = new VndAmount(initialSupply).requirePositive();
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public void initialize() {
        requireSetupTransaction();
        Objects.requireNonNull(admins.ensureConfiguredAdmin(), "configured Admin");
        var treasuryId = Objects.requireNonNull(treasuries.openTreasury(), "Treasury identity");
        var pending = Objects.requireNonNull(genesis.initialize(treasuryId, initialSupply), "pending GENESIS");
        if (!pending.amount().equals(initialSupply)) {
            throw new IllegalStateException("Pending GENESIS amount differs from the requested initial supply");
        }
        if (TransactionAspectSupport.currentTransactionStatus().isRollbackOnly()) {
            throw new UnexpectedRollbackException("Setup attempt was marked rollback-only");
        }
        // Only the external transaction proxy's successful return confirms completion, not this Pending.
    }

    private static void requireSetupTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_READ_COMMITTED)
                    .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                || !TransactionAspectSupport.currentTransactionStatus().isNewTransaction()) {
            throw new IllegalTransactionStateException("Setup requires an intercepted outer READ COMMITTED write transaction");
        }
    }
}
