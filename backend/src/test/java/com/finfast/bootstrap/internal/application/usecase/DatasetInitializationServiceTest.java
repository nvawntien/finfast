package com.finfast.bootstrap.internal.application.usecase;

import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.common.money.VndAmount;
import com.finfast.identity.api.AdminProvisioning;
import com.finfast.ledger.api.GenesisPosting;
import com.finfast.ledger.api.LedgerQueries;
import com.finfast.ledger.api.PostingResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = DatasetInitializationService.class)
@TestPropertySource(properties = "finfast.bootstrap.enabled=true")
class DatasetInitializationServiceTest {

    @MockitoBean
    private AdminProvisioning adminProvisioning;

    @MockitoBean
    private TreasuryProvisioning treasuryProvisioning;

    @MockitoBean
    private GenesisPosting genesisPosting;

    @MockitoBean
    private LedgerQueries ledgerQueries;

    @Autowired
    private DatasetInitializationService datasetInitializationService;

    @Test
    void initializeDataset_commitsAll_whenSuccessful() {
        when(ledgerQueries.isDatasetInitialized()).thenReturn(false);
        UUID treasuryAccountId = UUID.randomUUID();
        when(treasuryProvisioning.openTreasury()).thenReturn(treasuryAccountId);
        when(genesisPosting.initialize(eq(treasuryAccountId), any(VndAmount.class)))
                .thenReturn(new PostingResult.Pending(UUID.randomUUID(), "GENESIS-1", new VndAmount(100_000_000L), Instant.now()));

        assertThatNoException().isThrownBy(() -> datasetInitializationService.initializeDataset());

        verify(ledgerQueries).isDatasetInitialized();
        verify(adminProvisioning).ensureConfiguredAdmin();
        verify(treasuryProvisioning).openTreasury();
        verify(genesisPosting).initialize(eq(treasuryAccountId), eq(new VndAmount(100_000_000L)));
    }

    @Test
    void initializeDataset_aborts_whenDatasetAlreadyExists() {
        when(ledgerQueries.isDatasetInitialized()).thenReturn(true);

        assertThatIllegalStateException()
                .isThrownBy(() -> datasetInitializationService.initializeDataset())
                .withMessage("Dataset is already initialized. Reseeding is strictly forbidden.");

        verify(ledgerQueries).isDatasetInitialized();
        verify(adminProvisioning, never()).ensureConfiguredAdmin();
        verify(treasuryProvisioning, never()).openTreasury();
        verify(genesisPosting, never()).initialize(any(), any());
    }

    @Test
    void initializeDataset_rollsBack_onConcurrentRace() {
        when(ledgerQueries.isDatasetInitialized()).thenReturn(false);
        UUID treasuryAccountId = UUID.randomUUID();
        when(treasuryProvisioning.openTreasury()).thenReturn(treasuryAccountId);
        when(genesisPosting.initialize(eq(treasuryAccountId), any(VndAmount.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        assertThatIllegalStateException()
                .isThrownBy(() -> datasetInitializationService.initializeDataset())
                .withMessage("Concurrent initialization detected. Initialization aborted.");

        verify(ledgerQueries).isDatasetInitialized();
        verify(adminProvisioning).ensureConfiguredAdmin();
        verify(treasuryProvisioning).openTreasury();
        verify(genesisPosting).initialize(eq(treasuryAccountId), eq(new VndAmount(100_000_000L)));
    }
}

