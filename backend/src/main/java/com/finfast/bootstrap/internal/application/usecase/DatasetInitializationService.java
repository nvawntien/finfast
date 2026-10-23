package com.finfast.bootstrap.internal.application.usecase;

import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.common.money.VndAmount;
import com.finfast.identity.api.AdminProvisioning;
import com.finfast.ledger.api.GenesisPosting;
import com.finfast.ledger.api.PostingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class DatasetInitializationService {

    private static final Logger log = LoggerFactory.getLogger(DatasetInitializationService.class);

    private final AdminProvisioning adminProvisioning;
    private final TreasuryProvisioning treasuryProvisioning;
    private final GenesisPosting genesisPosting;

    public DatasetInitializationService(AdminProvisioning adminProvisioning,
                                        TreasuryProvisioning treasuryProvisioning,
                                        GenesisPosting genesisPosting) {
        this.adminProvisioning = Objects.requireNonNull(adminProvisioning, "adminProvisioning");
        this.treasuryProvisioning = Objects.requireNonNull(treasuryProvisioning, "treasuryProvisioning");
        this.genesisPosting = Objects.requireNonNull(genesisPosting, "genesisPosting");
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void initializeDataset() {
        log.info("Starting dataset initialization...");
        adminProvisioning.ensureConfiguredAdmin();
        UUID treasuryAccountId = treasuryProvisioning.openTreasury();
        VndAmount initialSupply = new VndAmount(100000000L);
        PostingResult.Pending genesis = genesisPosting.initialize(treasuryAccountId, initialSupply);
        log.info("Dataset initialized successfully with GENESIS");
    }
}

