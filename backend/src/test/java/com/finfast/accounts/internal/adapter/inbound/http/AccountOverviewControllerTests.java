package com.finfast.accounts.internal.adapter.inbound.http;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.common.error.GlobalExceptionHandler;
import com.finfast.common.money.VndAmount;
import com.finfast.identity.internal.adapter.inbound.http.SecurityExceptionAdvice;
import com.finfast.identity.internal.application.dto.SessionPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.accept.ContentNegotiationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Adapter mapping/defensive checks only; real login/session/role evidence is in AccountOverviewSecurityIntegrationTests. */
@ExtendWith(MockitoExtension.class)
class AccountOverviewControllerTests {
    @Mock AccountQueries queries;
    private MockMvc mvc;
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private UsernamePasswordAuthenticationToken authentication;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AccountOverviewController(queries))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new SecurityExceptionAdvice(), new GlobalExceptionHandler(new ContentNegotiationManager()))
                .build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void authenticated(String role) {
        authentication = UsernamePasswordAuthenticationToken.authenticated(
                new SessionPrincipal(USER, role, "CUSTOMER".equals(role) ? CUSTOMER : null, "Synthetic"), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    private CustomerAccount account(UUID owner, CustomerAccount.Status status) {
        return new CustomerAccount(UUID.randomUUID(), owner, new AccountNumber("ACC-0000000001"), status, new VndAmount(250000));
    }
    @Test void returnsAccountOverviewWhenAuthenticatedWithActiveAccount() throws Exception {
        authenticated("CUSTOMER");
        when(queries.findByCustomerId(CUSTOMER)).thenReturn(Optional.of(account(CUSTOMER, CustomerAccount.Status.ACTIVE)));
        mvc.perform(get("/api/v1/accounts/me").principal(authentication))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.accountNumber").value("ACC-0000000001"))
                .andExpect(jsonPath("$.type").value("CUSTOMER")).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currency").value("VND")).andExpect(jsonPath("$.balanceVnd").value("250000"))
                .andExpect(jsonPath("$.availableBalanceVnd").value("250000"))
                .andExpect(jsonPath("$.customerId").doesNotExist()).andExpect(jsonPath("$.accountId").doesNotExist());
        verify(queries).findByCustomerId(CUSTOMER);
        verify(queries, never()).findByCustomerId(USER);
    }
    @Test void allowsCustomerToViewOverviewWhenAccountIsFrozen() throws Exception {
        authenticated("CUSTOMER");
        when(queries.findByCustomerId(CUSTOMER)).thenReturn(Optional.of(account(CUSTOMER, CustomerAccount.Status.FROZEN)));
        mvc.perform(get("/api/v1/accounts/me").principal(authentication))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.balanceVnd").value("250000"));
    }
    @Test void returnsUnauthorizedWhenNoCustomerIdentityPresent() throws Exception {
        mvc.perform(get("/api/v1/accounts/me"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(queries);
    }
    @Test void returnsForbiddenWhenCustomerAccountNotFound() throws Exception {
        authenticated("CUSTOMER");
        when(queries.findByCustomerId(CUSTOMER)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/accounts/me").principal(authentication))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied."));
    }
    @Test void ignoresClientControlledRequestAttribute() throws Exception {
        mvc.perform(get("/api/v1/accounts/me").requestAttr("authenticatedCustomerId", CUSTOMER)
                        .param("customerId", CUSTOMER.toString()).header("X-Customer-Id", CUSTOMER))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(queries);
    }
    @Test void wrongRoleCannotInvokeAccountQueries() throws Exception {
        authenticated("ADMIN");
        mvc.perform(get("/api/v1/accounts/me").principal(authentication))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(queries);
    }
    @Test void mismatchedOwnerCannotBeDisclosed() throws Exception {
        authenticated("CUSTOMER");
        when(queries.findByCustomerId(CUSTOMER)).thenReturn(Optional.of(account(UUID.randomUUID(), CustomerAccount.Status.ACTIVE)));
        mvc.perform(get("/api/v1/accounts/me").principal(authentication))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.accountNumber").doesNotExist());
    }
}
