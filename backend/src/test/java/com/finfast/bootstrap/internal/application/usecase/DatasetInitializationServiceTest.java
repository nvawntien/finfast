package com.finfast.bootstrap.internal.application.usecase;

import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.common.money.VndAmount;
import com.finfast.identity.api.AdminProvisioning;
import com.finfast.ledger.api.GenesisPosting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatasetInitializationServiceTest {

    @Mock
    private AdminProvisioning adminProvisioning;

    @Mock
    private TreasuryProvisioning treasuryProvisioning;

    @Mock
    private GenesisPosting genesisPosting;

    @Test
    void initializeDataset_commitsAll_whenSuccessful() {
        // Khởi tạo Service thủ công, bơm các dependencies giả (mock) vào
        DatasetInitializationService service = new DatasetInitializationService(
                adminProvisioning, treasuryProvisioning, genesisPosting, 100000000L);

        UUID dummyTreasuryId = UUID.randomUUID();
        when(treasuryProvisioning.openTreasury()).thenReturn(dummyTreasuryId);

        assertThatNoException().isThrownBy(() -> service.initializeDataset());

        verify(adminProvisioning).ensureConfiguredAdmin();
        verify(treasuryProvisioning).openTreasury();
        verify(genesisPosting).initialize(eq(dummyTreasuryId), any(VndAmount.class));
    }

    @Test
    void initializeDataset_aborts_whenDatasetAlreadyExists_orConcurrentRace() {
        // Khởi tạo Service thủ công
        DatasetInitializationService service = new DatasetInitializationService(
                adminProvisioning, treasuryProvisioning, genesisPosting, 100000000L);

        // Bắt lỗi Database giả lập
        when(treasuryProvisioning.openTreasury()).thenThrow(new DataIntegrityViolationException("Simulated DB Constraint Violation"));

        assertThatIllegalStateException()
                .isThrownBy(() -> service.initializeDataset())
                .withMessageContaining("Dataset is already initialized or concurrent initialization detected");

        // Chặn không cho gọi tới hàm Genesis
        verify(genesisPosting, never()).initialize(any(), any());
    }
}