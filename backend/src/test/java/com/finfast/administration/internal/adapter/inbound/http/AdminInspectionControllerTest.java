package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.administration.internal.application.dto.AdminAccountView;
import com.finfast.administration.internal.application.dto.AdminAuditRecordView;
import com.finfast.administration.internal.application.dto.AdminFinancialTransactionView;
import com.finfast.administration.internal.application.port.inbound.AdminInspectionUseCase;
import com.finfast.identity.api.CurrentIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminInspectionController.class)
@Import(AdminInspectionControllerTest.TestWebMvcConfig.class)
class AdminInspectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminInspectionUseCase adminInspectionUseCase;

    @MockitoBean
    private CurrentIdentity currentIdentity;

    @TestConfiguration
    static class TestWebMvcConfig implements WebMvcConfigurer {
        private final CurrentIdentity currentIdentity;

        @Autowired
        TestWebMvcConfig(CurrentIdentity currentIdentity) {
            this.currentIdentity = currentIdentity;
        }

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return CurrentIdentity.class.isAssignableFrom(parameter.getParameterType());
                }

                @Override
                public Object resolveArgument(MethodParameter parameter,
                                              ModelAndViewContainer mavContainer,
                                              NativeWebRequest webRequest,
                                              WebDataBinderFactory binderFactory) {
                    return currentIdentity;
                }
            });
        }
    }

    @Test
    @DisplayName("GET /api/v1/admin/accounts returns 200 OK with strict OpenAPI decimal strings and no internal IDs")
    void getAccountsReturnsOkWithContractCompliance() throws Exception {
        UUID customerId = UUID.randomUUID();
        AdminAccountView accountView = new AdminAccountView(
                "ACC0012345",
                customerId,
                "Nguyen Van A",
                "ACTIVE",
                "2500000"
        );
        when(adminInspectionUseCase.getAccounts()).thenReturn(List.of(accountView));

        mockMvc.perform(get("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].accountNumber").isString())
                .andExpect(jsonPath("$.items[0].accountNumber").value("ACC0012345"))
                .andExpect(jsonPath("$.items[0].customerId").isString())
                .andExpect(jsonPath("$.items[0].customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.items[0].displayName").isString())
                .andExpect(jsonPath("$.items[0].displayName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.items[0].status").isString())
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].balanceVnd").isString())
                .andExpect(jsonPath("$.items[0].balanceVnd").value("2500000"))
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
                .andExpect(jsonPath("$.items[0].reference").isString())
                .andExpect(jsonPath("$.items[0].reference").value("REF-TRF-001"))
                .andExpect(jsonPath("$.items[0].type").isString())
                .andExpect(jsonPath("$.items[0].type").value("INTERNAL_TRANSFER"))
                .andExpect(jsonPath("$.items[0].amountVnd").isString())
                .andExpect(jsonPath("$.items[0].amountVnd").value("150000"))
                .andExpect(jsonPath("$.items[0].currency").isString())
                .andExpect(jsonPath("$.items[0].currency").value("VND"))
                .andExpect(jsonPath("$.items[0].committedAt").isString())
                .andExpect(jsonPath("$.items[0].entries").isArray())
                .andExpect(jsonPath("$.items[0].entries[0].accountNumber").value("ACC1"))
                .andExpect(jsonPath("$.items[0].entries[0].deltaVnd").isString())
                .andExpect(jsonPath("$.items[0].entries[0].deltaVnd").value("-150000"))
                .andExpect(jsonPath("$.items[0].entries[1].deltaVnd").isString())
                .andExpect(jsonPath("$.items[0].entries[1].deltaVnd").value("150000"))
                .andExpect(jsonPath("$.items[0].transactionId").doesNotExist());

        verify(currentIdentity).requireAdmin();
        verify(adminInspectionUseCase).getFinancialTransactions();
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit-events returns 200 OK with complete fields and no internal id")
    void getAuditEventsReturnsOkWithContractCompliance() throws Exception {
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
                .andExpect(jsonPath("$.items[0].administratorId").isString())
                .andExpect(jsonPath("$.items[0].administratorId").value(adminId.toString()))
                .andExpect(jsonPath("$.items[0].action").isString())
                .andExpect(jsonPath("$.items[0].action").value("DEMO_ALLOCATION"))
                .andExpect(jsonPath("$.items[0].target").isString())
                .andExpect(jsonPath("$.items[0].target").value("ACC0012345"))
                .andExpect(jsonPath("$.items[0].reason").isString())
                .andExpect(jsonPath("$.items[0].reason").value("Initial test allocation"))
                .andExpect(jsonPath("$.items[0].outcome").isString())
                .andExpect(jsonPath("$.items[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].reference").value("TX-ALLOC-001"))
                .andExpect(jsonPath("$.items[0].id").doesNotExist());

        verify(currentIdentity).requireAdmin();
        verify(adminInspectionUseCase).getAuditEvents();
    }

    @Test
    @DisplayName("Unauthorized unauthenticated request returns 401 with ErrorResponse body and never invokes provider")
    void unauthenticatedRequestReturns401AndNeverInvokesProvider() throws Exception {
        when(currentIdentity.requireAdmin())
                .thenThrow(new SecurityException("Unauthenticated: session missing or expired"));

        mockMvc.perform(get("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isString());

        verify(currentIdentity).requireAdmin();
        verifyNoInteractions(adminInspectionUseCase);
    }

    @Test
    @DisplayName("Forbidden request lacking ADMIN role returns 403 with ErrorResponse body and never invokes provider")
    void forbiddenRequestWithoutAdminRoleReturns403AndNeverInvokesProvider() throws Exception {
        when(currentIdentity.requireAdmin())
                .thenThrow(new SecurityException("Access denied: ADMIN role required"));

        mockMvc.perform(get("/api/v1/admin/financial-transactions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.message").value("Access denied: ADMIN role required"));

        verify(currentIdentity).requireAdmin();
        verifyNoInteractions(adminInspectionUseCase);
    }
}
