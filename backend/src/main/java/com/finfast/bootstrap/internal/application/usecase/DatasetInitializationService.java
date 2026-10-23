package com.finfast.bootstrap.internal.application.usecase;

import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.common.money.VndAmount;
import com.finfast.identity.api.AdminProvisioning;
import com.finfast.ledger.api.GenesisPosting;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "finfast.bootstrap.enabled", havingValue = "true", matchIfMissing = false)
public class DatasetInitializationService {

    private final AdminProvisioning adminProvisioning;
    private final TreasuryProvisioning treasuryProvisioning;
    private final GenesisPosting genesisPosting;
    private final long initialSupplyValue;

    public DatasetInitializationService(
            AdminProvisioning adminProvisioning,
            TreasuryProvisioning treasuryProvisioning,
            GenesisPosting genesisPosting,
            @Value("${finfast.bootstrap.initial-supply:100000000}") long initialSupplyValue) {
        
        this.adminProvisioning = Objects.requireNonNull(adminProvisioning, "adminProvisioning");
        this.treasuryProvisioning = Objects.requireNonNull(treasuryProvisioning, "treasuryProvisioning");
        this.genesisPosting = Objects.requireNonNull(genesisPosting, "genesisPosting");
        
        if (initialSupplyValue <= 0) {
            throw new IllegalArgumentException("Initial supply must be strictly positive");
        }
        this.initialSupplyValue = initialSupplyValue;
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void initializeDataset() {
        try {
            // 1. Protect the operator setup (Identity module)
            adminProvisioning.ensureConfiguredAdmin();

            // 2. Open Treasury account (Accounts module)
            UUID treasuryAccountId = treasuryProvisioning.openTreasury();

            // 3. Prepare intent
            VndAmount initialSupply = new VndAmount(this.initialSupplyValue);

            // 4. Initialize GENESIS (Ledger module)
            genesisPosting.initialize(treasuryAccountId, initialSupply);
            
        } catch (DataIntegrityViolationException ex) {
            // Giải quyết Lỗi #6 (Existing Dataset) và Lỗi #7 (Concurrent Race)
            throw new IllegalStateException("Dataset is already initialized or concurrent initialization detected. Reseeding is strictly forbidden.", ex);
        }
    }
}