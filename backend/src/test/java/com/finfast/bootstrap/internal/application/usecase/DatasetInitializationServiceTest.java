package com.finfast.bootstrap.internal.application.usecase;

import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.bootstrap.internal.application.port.inbound.GenesisDatasetInspection;
import com.finfast.common.money.VndAmount;
import com.finfast.identity.api.AdminProvisioning;
import com.finfast.ledger.api.GenesisPosting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetInitializationServiceTest {

    @Mock
    private AdminProvisioning adminProvisioning;

    @Mock
    private TreasuryProvisioning treasuryProvisioning;

    @Mock
    private GenesisPosting genesisPosting;

    @Mock
    private GenesisDatasetInspection genesisDatasetInspection;

    private DatasetInitializationService service;

    @BeforeEach
    void setUp() {
        service = new DatasetInitializationService(
                adminProvisioning,
                treasuryProvisioning,
                genesisPosting,
                genesisDatasetInspection,
                100000000L
        );
    }

    @Test
    @DisplayName("Verify orchestration across identity, accounts, and ledger modules when successful")
    void verifyOrchestration_whenSuccessful() {
        when(genesisDatasetInspection.isGenesisInitialized()).thenReturn(false);

        UUID dummyTreasuryId = UUID.randomUUID();
        when(treasuryProvisioning.openTreasury()).thenReturn(dummyTreasuryId);

        assertThatNoException().isThrownBy(() -> service.initializeDataset());

        verify(genesisDatasetInspection).isGenesisInitialized();
        verify(adminProvisioning).ensureConfiguredAdmin();
        verify(treasuryProvisioning).openTreasury();
        verify(genesisPosting).initialize(eq(dummyTreasuryId), any(VndAmount.class));
    }

    @Test
    @DisplayName("Returns early without invoking providers when dataset is already initialized")
    void initializeDataset_returnsEarly_whenAlreadyInitialized() {
        when(genesisDatasetInspection.isGenesisInitialized()).thenReturn(true);

        assertThatNoException().isThrownBy(() -> service.initializeDataset());

        verify(genesisDatasetInspection).isGenesisInitialized();
        verifyNoInteractions(adminProvisioning, treasuryProvisioning, genesisPosting);
    }

    @Test
    @DisplayName("Recovers gracefully when DataIntegrityViolationException occurs and concurrent setup won")
    void initializeDataset_recoversGracefully_whenConcurrentSetupWon() {
        when(genesisDatasetInspection.isGenesisInitialized())
                .thenReturn(false)
                .thenReturn(true);

        when(treasuryProvisioning.openTreasury())
                .thenThrow(new DataIntegrityViolationException("Duplicate key: treasury account already exists"));

        assertThatNoException().isThrownBy(() -> service.initializeDataset());

        verify(genesisDatasetInspection, times(2)).isGenesisInitialized();
        verify(adminProvisioning).ensureConfiguredAdmin();
        verify(treasuryProvisioning).openTreasury();
        verify(genesisPosting, never()).initialize(any(), any());
    }

    @Test
    @DisplayName("Propagates DataIntegrityViolationException when verification in new transaction does not confirm initialization")
    void initializeDataset_propagatesFailure_whenDataIntegrityViolationAndConcurrentSetupDidNotWin() {
        when(genesisDatasetInspection.isGenesisInitialized())
                .thenReturn(false)
                .thenReturn(false);

        when(treasuryProvisioning.openTreasury())
                .thenThrow(new DataIntegrityViolationException("Foreign key violation"));

        assertThatThrownBy(() -> service.initializeDataset())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Foreign key violation");

        verify(genesisDatasetInspection, times(2)).isGenesisInitialized();
        verify(adminProvisioning).ensureConfiguredAdmin();
        verify(treasuryProvisioning).openTreasury();
        verify(genesisPosting, never()).initialize(any(), any());
    }
}
