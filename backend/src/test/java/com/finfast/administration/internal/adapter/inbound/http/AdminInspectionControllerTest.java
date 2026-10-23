package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.accounts.api.AdminAccountQueries;
import com.finfast.administration.internal.application.port.outbound.AdminAuditQueries;
import com.finfast.identity.api.CurrentIdentity;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminInspectionController.class)
class AdminInspectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentIdentity currentIdentity;

    @MockitoBean
    private AdminAccountQueries adminAccountQueries;

    @MockitoBean
    private AdminFinancialTransactionQueries adminFinancialTransactionQueries;

    @MockitoBean
    private AdminAuditQueries adminAuditQueries;

    @Test
    @DisplayName("GET /api/v1/admin/accounts returns 200 OK with empty items")
    void getAccountsReturnsOkWithEmptyList() throws Exception {
        when(adminAccountQueries.listCustomers()).thenReturn(new AdminAccountQueries.Listing(List.of()));

        mockMvc.perform(get("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(currentIdentity).requireAdmin();
        verify(adminAccountQueries).listCustomers();
    }

    @Test
    @DisplayName("GET /api/v1/admin/financial-transactions returns 200 OK with empty items")
    void getFinancialTransactionsReturnsOkWithEmptyList() throws Exception {
        when(adminFinancialTransactionQueries.listCommitted()).thenReturn(new AdminFinancialTransactionQueries.Transactions(List.of()));

        mockMvc.perform(get("/api/v1/admin/financial-transactions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(currentIdentity).requireAdmin();
        verify(adminFinancialTransactionQueries).listCommitted();
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit-events returns 200 OK with empty items")
    void getAuditEventsReturnsOkWithEmptyList() throws Exception {
        when(adminAuditQueries.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(currentIdentity).requireAdmin();
        verify(adminAuditQueries).listAll();
    }

    @Test
    @DisplayName("GET /api/v1/admin/accounts rejects non-admin identity with 4xx status")
    void getAccountsBlocksAccessWhenAdminRoleMissing() throws Exception {
        when(currentIdentity.requireAdmin())
                .thenThrow(new SecurityException("Access denied: ADMIN role required"));

        mockMvc.perform(get("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(adminAccountQueries, adminFinancialTransactionQueries, adminAuditQueries);
    }

    @Test
    @DisplayName("GET /api/v1/admin/financial-transactions rejects non-admin identity with 4xx status")
    void getFinancialTransactionsBlocksAccessWhenAdminRoleMissing() throws Exception {
        when(currentIdentity.requireAdmin())
                .thenThrow(new SecurityException("Access denied: ADMIN role required"));

        mockMvc.perform(get("/api/v1/admin/financial-transactions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(adminAccountQueries, adminFinancialTransactionQueries, adminAuditQueries);
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit-events rejects non-admin identity with 4xx status")
    void getAuditEventsBlocksAccessWhenAdminRoleMissing() throws Exception {
        when(currentIdentity.requireAdmin())
                .thenThrow(new SecurityException("Access denied: ADMIN role required"));

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(adminAccountQueries, adminFinancialTransactionQueries, adminAuditQueries);
    }
}


