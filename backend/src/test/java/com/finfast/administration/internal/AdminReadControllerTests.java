package com.finfast.administration.internal;

import com.finfast.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminReadController.class)
@Import({GlobalExceptionHandler.class, AdminSecurityWebConfig.class, AdminRbacInterceptor.class})
class AdminReadControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminReadService adminReadService;

    @Test
    void rejectsAnonymousAccessToTreasuryStatusWith403() throws Exception {
        mvc.perform(get("/api/v1/admin/treasury/status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectsCustomerAccessToTreasuryStatusWith403() throws Exception {
        mvc.perform(get("/api/v1/admin/treasury/status")
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void allowsAdminAccessToTreasuryStatus() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID genesisId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-22T04:44:24Z");

        TreasuryStatusResponse response = new TreasuryStatusResponse(
                accountId, "TREASURY", "ACTIVE", "VND",
                "10000000000", "10000000000", genesisId, now);

        when(adminReadService.getTreasuryStatus()).thenReturn(response);

        mvc.perform(get("/api/v1/admin/treasury/status")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.accountNumber").value("TREASURY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balanceVnd").value("10000000000"))
                .andExpect(jsonPath("$.initialSupplyVnd").value("10000000000"))
                .andExpect(jsonPath("$.genesisTransactionId").value(genesisId.toString()));
    }

    @Test
    void allowsAdminAccessToTransactionsWithPaginationAndFilters() throws Exception {
        UUID txId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-22T04:44:24Z");

        AdminFinancialTransactionResponse tx = new AdminFinancialTransactionResponse(
                txId, "GENESIS-01", "GENESIS", "10000000000", "VND", 1L, now,
                List.of(new AdminFinancialEntryResponse(accountId, "TREASURY", "10000000000")));

        PaginationMetadata pagination = new PaginationMetadata(1, 0, 20, 1, false, false);
        PaginatedResponse<AdminFinancialTransactionResponse> response = new PaginatedResponse<>(List.of(tx), pagination);

        when(adminReadService.getFinancialTransactions(eq("GENESIS"), any(), any(), eq(0), eq(20), eq("committed_at"), eq("DESC")))
                .thenReturn(response);

        mvc.perform(get("/api/v1/admin/transactions")
                        .header("X-User-Role", "ADMIN")
                        .param("type", "GENESIS")
                        .param("page", "0")
                        .param("limit", "20")
                        .param("sortBy", "committed_at")
                        .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reference").value("GENESIS-01"))
                .andExpect(jsonPath("$.items[0].type").value("GENESIS"))
                .andExpect(jsonPath("$.items[0].amountVnd").value("10000000000"))
                .andExpect(jsonPath("$.items[0].entries[0].accountNumber").value("TREASURY"))
                .andExpect(jsonPath("$.pagination.total").value(1))
                .andExpect(jsonPath("$.pagination.totalPages").value(1));
    }
}

