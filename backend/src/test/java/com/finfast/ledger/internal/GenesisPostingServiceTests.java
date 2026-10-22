package com.finfast.ledger.internal;

import com.finfast.accounts.api.PostingAccounts;
import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.PostingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenesisPostingServiceTests {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private PostingAccounts postingAccounts;

    private GenesisPostingService service;
    private final UUID treasuryId = UUID.randomUUID();
    private final VndAmount supply = new VndAmount(10_000_000_000L);

    @BeforeEach
    void setUp() {
        service = new GenesisPostingService(jdbc, postingAccounts);
    }

    @Test
    void rejectsNonPositiveInitialSupply() {
        VndAmount zeroSupply = new VndAmount(0);
        assertThatThrownBy(() -> service.initialize(treasuryId, zeroSupply))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullTreasuryAccountId() {
        assertThatThrownBy(() -> service.initialize(null, supply))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsIfDatasetControlAlreadyInitialized() {
        when(jdbc.queryForObject(contains("dataset_control"), eq(Integer.class))).thenReturn(1);
        when(jdbc.queryForObject(contains("initial_supply_vnd"), eq(Long.class))).thenReturn(10_000_000_000L);

        assertThatThrownBy(() -> service.initialize(treasuryId, supply))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already initialized");

        verify(postingAccounts, never()).initializeTreasury(any(), any());
    }

    @Test
    void rejectsIfGenesisTransactionAlreadyExists() {
        when(jdbc.queryForObject(contains("dataset_control"), eq(Integer.class))).thenReturn(0);
        when(jdbc.queryForObject(contains("financial_transaction"), eq(Integer.class))).thenReturn(1);

        assertThatThrownBy(() -> service.initialize(treasuryId, supply))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GENESIS transaction already exists");

        verify(postingAccounts, never()).initializeTreasury(any(), any());
    }

    @Test
    void initializesGenesisSuccessfully() {
        when(jdbc.queryForObject(contains("dataset_control"), eq(Integer.class))).thenReturn(0);
        when(jdbc.queryForObject(contains("financial_transaction"), eq(Integer.class))).thenReturn(0);

        PostingResult.Pending result = service.initialize(treasuryId, supply);

        assertThat(result).isNotNull();
        assertThat(result.amount()).isEqualTo(supply);
        assertThat(result.reference()).startsWith("GENESIS-");
        assertThat(result.transactionId()).isNotNull();

        verify(postingAccounts, times(1)).initializeTreasury(treasuryId, supply);
        verify(jdbc, times(3)).update(anyString(), any(Object[].class));
    }
}

