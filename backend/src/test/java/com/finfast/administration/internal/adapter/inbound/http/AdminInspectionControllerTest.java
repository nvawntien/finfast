package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.administration.internal.application.dto.AdminAccountView;
import com.finfast.administration.internal.application.dto.AdminAuditRecordView;
import com.finfast.administration.internal.application.dto.AdminFinancialTransactionView;
import com.finfast.administration.internal.application.port.inbound.AdminInspectionUseCase;
import com.finfast.administration.internal.domain.exception.ForbiddenException;
import com.finfast.administration.internal.domain.exception.ProviderNotReadyException;
import com.finfast.administration.internal.domain.exception.UnauthorizedException;
import com.finfast.identity.api.CurrentIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminInspectionController.class)
@Import(AdminExceptionHandler.class)
class AdminInspectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminInspectionUseCase adminInspectionUseCase;

    @MockitoBean
    private CurrentIdentity currentIdentity;

    @Test
    @DisplayName("GET /api/v1/admin/accounts returns 200 OK without filtering valid accounts")
    void getAccountsReturnsAllValidAccounts() throws Exception {
        UUID customerId1 = UUID.randomUUID();
        UUID customerId2 = UUID.randomUUID();

        AdminAccountView accountView1 = new AdminAccountView(
                "ACC0012345",
                customerId1,
                "Nguyen Van A",
                "ACTIVE",
                "2500000"
        );
        AdminAccountView accountView2 = new AdminAccountView(
                "ACC9876543",
                customerId2,
                "Customer ACC9876543",
                "FROZEN",
                "50000000"
        );

        when(adminInspectionUseCase.getAccounts()).thenReturn(List.of(accountView1, accountView2));

        mockMvc.perform(get("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].accountNumber").value("ACC0012345"))
                .andExpect(jsonPath("$.items[0].customerId").value(customerId1.toString()))
                .andExpect(jsonPath("$.items[0].displayName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].balanceVnd").value("2500000"))
                .andExpect(jsonPath("$.items[1].accountNumber").value("ACC9876543"))
                .andExpect(jsonPath("$.items[1].customerId").value(customerId2.toString()))
                .andExpect(jsonPath("$.items[1].displayName").value("Customer ACC9876543"))
                .andExpect(jsonPath("$.items[0].id").doesNotExist())
                .andExpect(jsonPath("$.items[0].accountId").doesNotExist());

        verify(currentIdentity).requireAdmin();
        verify(adminInspectionUseCase).getAccounts();
    }

    @Test
    @DisplayName("GET /api/v1/admin/financial-transactions returns 200 OK with strict decimal strings and no internal transactionId")
    void getFinancialTransactionsReturnsOkWithContractCompliance() throws Exception {
        Instant committedAt = Instant.now();
        AdminFinancialTransactionView txView = new AdminFinancialTransactionView(
                "REF-TRF-001",
                "INTERNAL_TRANSFER",
                "150000",
                "VND",
                committedAt,
                List.of(
                        new AdminFinancialTransactionView.EntryView("ACC1", "-150000"),
                        new AdminFinancialTransactionView.EntryView("ACC2", "150000")
                )
        );
        when(adminInspectionUseCase.getFinancialTransactions()).thenReturn(List.of(txView));

        mockMvc.perform(get("/api/v1/admin/financial-transactions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].reference").value("REF-TRF-001"))
                .andExpect(jsonPath("$.items[0].type").value("INTERNAL_TRANSFER"))
                .andExpect(jsonPath("$.items[0].amountVnd").value("150000"))
                .andExpect(jsonPath("$.items[0].currency").value("VND"))
                .andExpect(jsonPath("$.items[0].committedAt").isString())
                .andExpect(jsonPath("$.items[0].entries").isArray())
                .andExpect(jsonPath("$.items[0].entries[0].accountNumber").value("ACC1"))
                .andExpect(jsonPath("$.items[0].entries[0].deltaVnd").value("-150000"))
                .andExpect(jsonPath("$.items[0].entries[1].deltaVnd").value("150000"))
                .andExpect(jsonPath("$.items[0].transactionId").doesNotExist());

        verify(currentIdentity).requireAdmin();
        verify(adminInspectionUseCase).getFinancialTransactions();
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit-events returns 200 OK with null optional fields serialized correctly")
    void getAuditEventsSerializesNullOptionalFieldsCorrectly() throws Exception {
        Instant timestamp = Instant.now();
        UUID adminId = UUID.randomUUID();
        AdminAuditRecordView auditView = new AdminAuditRecordView(
                timestamp,
                adminId,
                "DEMO_ALLOCATION",
                "ACC0012345",
                "Initial test allocation",
                "SUCCESS",
                "TX-ALLOC-001",
                null,
                null
        );
        when(adminInspectionUseCase.getAuditEvents()).thenReturn(List.of(auditView));

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].timestamp").isString())
                .andExpect(jsonPath("$.items[0].administratorId").value(adminId.toString()))
                .andExpect(jsonPath("$.items[0].action").value("DEMO_ALLOCATION"))
                .andExpect(jsonPath("$.items[0].target").value("ACC0012345"))
                .andExpect(jsonPath("$.items[0].reason").value("Initial test allocation"))
                .andExpect(jsonPath("$.items[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].reference").value("TX-ALLOC-001"))
                .andExpect(jsonPath("$.items[0].beforeStatus").doesNotExist())
                .andExpect(jsonPath("$.items[0].afterStatus").doesNotExist())
                .andExpect(jsonPath("$.items[0].id").doesNotExist());

        verify(currentIdentity).requireAdmin();
        verify(adminInspectionUseCase).getAuditEvents();
    }

    @Test
    @DisplayName("401 Unauthorized is returned using typed UnauthorizedException")
    void unauthorizedRequestWithTypedExceptionReturns401() throws Exception {
        when(currentIdentity.requireAdmin())
                .thenThrow(new UnauthorizedException("Authentication token missing or expired"));

        mockMvc.perform(get("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication token missing or expired"));

        verify(currentIdentity).requireAdmin();
        verifyNoInteractions(adminInspectionUseCase);
    }

    @Test
    @DisplayName("403 Forbidden is returned using typed ForbiddenException")
    void forbiddenRequestWithTypedExceptionReturns403() throws Exception {
        when(currentIdentity.requireAdmin())
                .thenThrow(new ForbiddenException("Access denied: ADMIN role required"));

        mockMvc.perform(get("/api/v1/admin/financial-transactions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied: ADMIN role required"));

        verify(currentIdentity).requireAdmin();
        verifyNoInteractions(adminInspectionUseCase);
    }

    @Test
    @DisplayName("500 Internal Server Error does not leak sensitive internal exception message")
    void genericExceptionReturns500WithoutLeakingDetails() throws Exception {
        when(adminInspectionUseCase.getAccounts())
                .thenThrow(new RuntimeException("FATAL: password authentication failed for user 'postgres' at jdbc:postgresql://10.0.0.1:5432"));

        mockMvc.perform(get("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value(not(containsString("password"))))
                .andExpect(jsonPath("$.message").value(not(containsString("postgres"))));

        verify(currentIdentity).requireAdmin();
        verify(adminInspectionUseCase).getAccounts();
    }

    @Test
    @DisplayName("503 Service Unavailable is returned when Optional providers are empty")
    void missingProviderReturns503ServiceUnavailable() throws Exception {
        when(adminInspectionUseCase.getAccounts())
                .thenThrow(new ProviderNotReadyException("AdminAccountQueries provider not ready"));

        mockMvc.perform(get("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("AdminAccountQueries provider not ready"));

        verify(currentIdentity).requireAdmin();
        verify(adminInspectionUseCase).getAccounts();
    }

    @Test
    @DisplayName("CustomerQueries failure propagates as 500 rather than falling back to 200 with fake data")
    void customerQueriesFailurePropagatesAs500() throws Exception {
        when(adminInspectionUseCase.getAccounts())
                .thenThrow(new RuntimeException("Customer service read timeout"));

        mockMvc.perform(get("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal Server Error"));

        verify(currentIdentity).requireAdmin();
        verify(adminInspectionUseCase).getAccounts();
    }
}
