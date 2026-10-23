package com.finfast.accounts.internal.adapter.inbound.http;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.common.money.VndAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountOverviewControllerTests {

    @Mock
    private AccountQueries accountQueries;

    private MockMvc mvc;

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final AccountNumber ACCOUNT_NUMBER = new AccountNumber("ACC-0000000001");

    @BeforeEach
    void setUp() {
        AccountOverviewController controller = new AccountOverviewController(accountQueries);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returnsAccountOverviewWhenAuthenticatedWithActiveAccount() throws Exception {
        CustomerAccount account = new CustomerAccount(
                ACCOUNT_ID, CUSTOMER_ID, ACCOUNT_NUMBER, CustomerAccount.Status.ACTIVE, new VndAmount(250_000));
        when(accountQueries.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(account));

        mvc.perform(get("/api/v1/accounts/me")
                        .requestAttr(AccountOverviewController.ATTR_CUSTOMER_ID, CUSTOMER_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accountNumber").value("ACC-0000000001"))
                .andExpect(jsonPath("$.type").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.balanceVnd").value("250000"))
                .andExpect(jsonPath("$.availableBalanceVnd").value("250000"));

        verify(accountQueries).findByCustomerId(CUSTOMER_ID);
    }

    @Test
    void allowsCustomerToViewOverviewWhenAccountIsFrozen() throws Exception {
        CustomerAccount account = new CustomerAccount(
                ACCOUNT_ID, CUSTOMER_ID, ACCOUNT_NUMBER, CustomerAccount.Status.FROZEN, new VndAmount(50_000));
        when(accountQueries.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(account));

        mvc.perform(get("/api/v1/accounts/me")
                        .requestAttr(AccountOverviewController.ATTR_CUSTOMER_ID, CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.balanceVnd").value("50000"))
                .andExpect(jsonPath("$.availableBalanceVnd").value("50000"));
    }

    @Test
    void returnsUnauthorizedWhenNoCustomerIdentityPresent() throws Exception {
        mvc.perform(get("/api/v1/accounts/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accountQueries);
    }

    @Test
    void returnsForbiddenWhenCustomerAccountNotFound() throws Exception {
        when(accountQueries.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/accounts/me")
                        .requestAttr(AccountOverviewController.ATTR_CUSTOMER_ID, CUSTOMER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void ignoresClientControlledCustomerHeader() throws Exception {
        mvc.perform(get("/api/v1/accounts/me")
                        .header("X-Customer-Id", CUSTOMER_ID.toString()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accountQueries);
    }
}
