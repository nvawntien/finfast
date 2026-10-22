package com.finfast.accounts.internal;

import com.finfast.common.money.VndAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostingAccountsServiceTests {

    @Mock
    private JdbcTemplate jdbc;

    private PostingAccountsService service;
    private final UUID treasuryId = UUID.randomUUID();
    private final VndAmount supply = new VndAmount(10_000_000_000L);

    @BeforeEach
    void setUp() {
        service = new PostingAccountsService(jdbc);
    }

    @Test
    void rejectsNullTreasuryAccountId() {
        assertThatThrownBy(() -> service.initializeTreasury(null, supply))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNonPositiveInitialSupply() {
        VndAmount zeroSupply = new VndAmount(0);
        assertThatThrownBy(() -> service.initializeTreasury(treasuryId, zeroSupply))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatesTreasuryBalanceSuccessfully() {
        when(jdbc.update(contains("UPDATE account SET balance_vnd"), eq(supply.value()), eq(treasuryId)))
                .thenReturn(1);

        assertThatNoException().isThrownBy(() -> service.initializeTreasury(treasuryId, supply));

        verify(jdbc, times(1)).update(contains("UPDATE account SET balance_vnd"), eq(supply.value()), eq(treasuryId));
    }

    @Test
    void rejectsIfTreasuryAlreadyFunded() {
        when(jdbc.update(contains("UPDATE account SET balance_vnd"), eq(supply.value()), eq(treasuryId)))
                .thenReturn(0);
        when(jdbc.query(contains("SELECT balance_vnd FROM account"), any(ResultSetExtractor.class), eq(treasuryId)))
                .thenReturn(5_000_000_000L);

        assertThatThrownBy(() -> service.initializeTreasury(treasuryId, supply))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already funded");
    }

    @Test
    void rejectsIfTreasuryAccountNotFound() {
        when(jdbc.update(contains("UPDATE account SET balance_vnd"), eq(supply.value()), eq(treasuryId)))
                .thenReturn(0);
        when(jdbc.query(contains("SELECT balance_vnd FROM account"), any(ResultSetExtractor.class), eq(treasuryId)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.initializeTreasury(treasuryId, supply))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
    }
}

