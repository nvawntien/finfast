package com.finfast.bootstrap.internal;

import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.GenesisPosting;
import com.finfast.ledger.api.PostingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TreasuryBootstrapTests {

    @Mock
    private TreasuryProvisioning treasuryProvisioning;

    @Mock
    private GenesisPosting genesisPosting;

    private TreasuryBootstrap bootstrap;
    private final UUID treasuryId = UUID.randomUUID();
    private final long supply = 10_000_000_000L;

    @BeforeEach
    void setUp() {
        bootstrap = new TreasuryBootstrap(treasuryProvisioning, genesisPosting, supply);
    }

    @Test
    void runsGenesisInitializationSuccessfully() {
        when(treasuryProvisioning.openTreasury()).thenReturn(treasuryId);
        PostingResult.Pending pending = new PostingResult.Pending(
                UUID.randomUUID(), "GENESIS-01", new VndAmount(supply), Instant.now());
        when(genesisPosting.initialize(eq(treasuryId), eq(new VndAmount(supply)))).thenReturn(pending);

        assertThatNoException().isThrownBy(() -> bootstrap.run());

        verify(treasuryProvisioning, times(1)).openTreasury();
        verify(genesisPosting, times(1)).initialize(treasuryId, new VndAmount(supply));
    }

    @Test
    void handlesExistingTreasuryInitializationIdempotently() {
        when(treasuryProvisioning.openTreasury()).thenReturn(treasuryId);
        when(genesisPosting.initialize(any(), any()))
                .thenThrow(new IllegalStateException("Treasury is already initialized with supply: 10000000000 VND"));

        // Must catch IllegalStateException, log message and not rethrow
        assertThatNoException().isThrownBy(() -> bootstrap.run());

        verify(treasuryProvisioning, times(1)).openTreasury();
        verify(genesisPosting, times(1)).initialize(treasuryId, new VndAmount(supply));
    }
}

