package com.finfast.bootstrap.internal.application.usecase;

import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.bootstrap.internal.application.port.inbound.GenesisDatasetInspection;
import com.finfast.bootstrap.internal.application.port.inbound.InitializeDatasetUseCase;
import com.finfast.common.money.VndAmount;
import com.finfast.identity.api.AdminProvisioning;
import com.finfast.ledger.api.GenesisPosting;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

public class DatasetInitializationService implements InitializeDatasetUseCase {

    private final AdminProvisioning adminProvisioning;
    private final TreasuryProvisioning treasuryProvisioning;
    private final GenesisPosting genesisPosting;
    private final GenesisDatasetInspection genesisDatasetInspection;
    private final long initialSupplyValue;
    private final TransactionTemplate transactionTemplate;

    public DatasetInitializationService(
            AdminProvisioning adminProvisioning,
            TreasuryProvisioning treasuryProvisioning,
            GenesisPosting genesisPosting,
            GenesisDatasetInspection genesisDatasetInspection,
            long initialSupplyValue) {
        this(adminProvisioning, treasuryProvisioning, genesisPosting, genesisDatasetInspection, initialSupplyValue, null);
    }

    public DatasetInitializationService(
            AdminProvisioning adminProvisioning,
            TreasuryProvisioning treasuryProvisioning,
            GenesisPosting genesisPosting,
            GenesisDatasetInspection genesisDatasetInspection,
            long initialSupplyValue,
            TransactionTemplate transactionTemplate) {

        this.adminProvisioning = Objects.requireNonNull(adminProvisioning, "adminProvisioning");
        this.treasuryProvisioning = Objects.requireNonNull(treasuryProvisioning, "treasuryProvisioning");
        this.genesisPosting = Objects.requireNonNull(genesisPosting, "genesisPosting");
        this.genesisDatasetInspection = Objects.requireNonNull(genesisDatasetInspection, "genesisDatasetInspection");

        if (initialSupplyValue <= 0) {
            throw new IllegalArgumentException("Initial supply must be strictly positive");
        }
        this.initialSupplyValue = initialSupplyValue;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void initializeDataset() {
        // a. Call genesisDatasetInspection.isGenesisInitialized() first. If true, return early.
        if (genesisDatasetInspection.isGenesisInitialized()) {
            return;
        }

        try {
            // b. Proceed with calling AdminProvisioning, TreasuryProvisioning, and GenesisPosting.
            adminProvisioning.ensureConfiguredAdmin();
            UUID treasuryAccountId = treasuryProvisioning.openTreasury();
            VndAmount initialSupply = new VndAmount(this.initialSupplyValue);
            genesisPosting.initialize(treasuryAccountId, initialSupply);
        } catch (DataIntegrityViolationException ex) {
            // c. Catch DataIntegrityViolationException. Verify dataset in a separate, fresh transaction.
            if (isGenesisInitializedInNewTransaction()) {
                return;
            }
            throw ex;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean isGenesisInitializedInNewTransaction() {
        if (transactionTemplate != null) {
            Boolean result = transactionTemplate.execute(status -> genesisDatasetInspection.isGenesisInitialized());
            return Boolean.TRUE.equals(result);
        }
        return genesisDatasetInspection.isGenesisInitialized();
    }
}
