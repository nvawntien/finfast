package com.finfast.administration.internal;

import com.finfast.common.error.ApplicationException;
import com.finfast.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReadServiceTests {

    @Mock
    private JdbcTemplate jdbc;

    private AdminReadService service;

    @BeforeEach
    void setUp() {
        service = new AdminReadService(jdbc);
    }

    @Test
    void getTreasuryStatusThrowsWhenNotFound() {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.getTreasuryStatus())
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> {
                    ApplicationException appEx = (ApplicationException) ex;
                    assertThat(appEx.category()).isEqualTo(ErrorCode.Category.NOT_FOUND);
                    assertThat(appEx.code()).isEqualTo("TREASURY_NOT_FOUND");
                });
    }

    @Test
    void getTreasuryStatusReturnsSuccessfulResponse() {
        UUID accountId = UUID.randomUUID();
        UUID genesisId = UUID.randomUUID();
        Instant now = Instant.now();

        TreasuryStatusResponse mockResponse = new TreasuryStatusResponse(
                accountId, "TREASURY", "ACTIVE", "VND",
                "10000000000", "10000000000", genesisId, now);

        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(mockResponse));

        TreasuryStatusResponse response = service.getTreasuryStatus();

        assertThat(response).isNotNull();
        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.accountNumber()).isEqualTo("TREASURY");
        assertThat(response.balanceVnd()).isEqualTo("10000000000");
        assertThat(response.initialSupplyVnd()).isEqualTo("10000000000");
    }

    @Test
    void getFinancialTransactionsValidatesPaginationParameters() {
        assertThatThrownBy(() -> service.getFinancialTransactions(null, null, null, -1, 20, "committed_at", "DESC"))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> assertThat(((ApplicationException) ex).category()).isEqualTo(ErrorCode.Category.INVALID_REQUEST));

        assertThatThrownBy(() -> service.getFinancialTransactions(null, null, null, 0, 0, "committed_at", "DESC"))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> assertThat(((ApplicationException) ex).category()).isEqualTo(ErrorCode.Category.INVALID_REQUEST));

        assertThatThrownBy(() -> service.getFinancialTransactions(null, null, null, 0, 101, "committed_at", "DESC"))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> assertThat(((ApplicationException) ex).category()).isEqualTo(ErrorCode.Category.INVALID_REQUEST));
    }

    @Test
    void getFinancialTransactionsValidatesSortAndFilterParameters() {
        assertThatThrownBy(() -> service.getFinancialTransactions("INVALID_TYPE", null, null, 0, 20, "committed_at", "DESC"))
                .isInstanceOf(ApplicationException.class);

        assertThatThrownBy(() -> service.getFinancialTransactions(null, null, null, 0, 20, "invalid_col", "DESC"))
                .isInstanceOf(ApplicationException.class);

        assertThatThrownBy(() -> service.getFinancialTransactions(null, null, null, 0, 20, "committed_at", "INVALID_DIR"))
                .isInstanceOf(ApplicationException.class);
    }

    @Test
    void getFinancialTransactionsReturnsEmptyWhenTotalIsZero() {
        when(jdbc.queryForObject(startsWith("SELECT count(*)"), eq(Long.class), any())).thenReturn(0L);

        PaginatedResponse<AdminFinancialTransactionResponse> response = service.getFinancialTransactions(
                "GENESIS", null, null, 0, 20, "committed_at", "DESC");

        assertThat(response.items()).isEmpty();
        assertThat(response.pagination().total()).isZero();
        assertThat(response.pagination().totalPages()).isZero();
        assertThat(response.pagination().hasNext()).isFalse();
    }
}

