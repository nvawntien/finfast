package com.finfast.administration.internal.application.usecase;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AdminAccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.administration.internal.application.dto.AdminAccountView;
import com.finfast.administration.internal.application.dto.AdminAuditRecordView;
import com.finfast.administration.internal.application.dto.AdminFinancialTransactionView;
import com.finfast.administration.internal.application.port.outbound.AdminAuditQueries;
import com.finfast.administration.internal.domain.exception.ProviderNotReadyException;
import com.finfast.common.money.VndAmount;
import com.finfast.customer.api.CustomerQueries;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInspectionServiceTest {

    @Mock
    private AdminAccountQueries adminAccountQueries;

    @Mock
    private AdminFinancialTransactionQueries adminFinancialTransactionQueries;

    @Mock
    private AdminAuditQueries adminAuditQueries;

    @Mock
    private CustomerQueries customerQueries;

    private AdminInspectionService service;

    @BeforeEach
    void setUp() {
        service = new AdminInspectionService(
                adminAccountQueries,
                adminFinancialTransactionQueries,
                adminAuditQueries,
                customerQueries
        );
    }

    @Test
    @DisplayName("Accounts list trusts AdminAccountQueries contract without manual treasury filtering")
    void getAccountsTrustsAdminAccountQueriesContract() {
        UUID customerId = UUID.randomUUID();
        CustomerAccount account1 = new CustomerAccount(
                UUID.randomUUID(),
                customerId,
                new AccountNumber("ACC1234567"),
                CustomerAccount.Status.ACTIVE,
                new VndAmount(500_000L)
        );

        CustomerAccount account2 = new CustomerAccount(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new AccountNumber("ACC9876543"),
                CustomerAccount.Status.ACTIVE,
                new VndAmount(100_000_000L)
        );

        when(adminAccountQueries.listCustomers()).thenReturn(
                new AdminAccountQueries.Listing(List.of(account1, account2))
        );
        when(customerQueries.findByCustomerId(customerId)).thenReturn(
                Optional.of(new CustomerQueries.Profile(customerId, UUID.randomUUID(), "Alice Wonderland"))
        );
        when(customerQueries.findByCustomerId(account2.customerId())).thenReturn(Optional.empty());

        List<AdminAccountView> result = service.getAccounts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).accountNumber()).isEqualTo("ACC1234567");
        assertThat(result.get(0).displayName()).isEqualTo("Alice Wonderland");
        assertThat(result.get(1).accountNumber()).isEqualTo("ACC9876543");
        assertThat(result.get(1).displayName()).isEqualTo("Customer ACC9876543");
    }

    @Test
    @DisplayName("CustomerQueries technical failures propagate naturally and are not swallowed")
    void getAccountsPropagatesCustomerQueriesFailures() {
        UUID customerId = UUID.randomUUID();
        CustomerAccount account = new CustomerAccount(
                UUID.randomUUID(),
                customerId,
                new AccountNumber("ACC1234567"),
                CustomerAccount.Status.ACTIVE,
                new VndAmount(500_000L)
        );

        when(adminAccountQueries.listCustomers()).thenReturn(
                new AdminAccountQueries.Listing(List.of(account))
        );
        when(customerQueries.findByCustomerId(customerId))
                .thenThrow(new RuntimeException("Customer service network timeout"));

        assertThatThrownBy(() -> service.getAccounts())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Customer service network timeout");
    }

    @Test
    @DisplayName("Throws ProviderNotReadyException when optional providers are missing")
    void throwsProviderNotReadyExceptionWhenProviderMissing() {
        AdminInspectionService incompleteService = new AdminInspectionService(
                Optional.empty(),
                Optional.of(adminFinancialTransactionQueries),
                Optional.of(adminAuditQueries),
                Optional.of(customerQueries)
        );

        assertThatThrownBy(() -> incompleteService.getAccounts())
                .isInstanceOf(ProviderNotReadyException.class)
                .hasMessageContaining("AdminAccountQueries provider not ready");
    }

    @Test
    @DisplayName("Accurate mapping of Financial Transactions: transfer, allocation, GENESIS")
    void getFinancialTransactionsMapsAllTypesAccurately() {
        Instant now = Instant.now();
        UUID txId = UUID.randomUUID();

        AdminFinancialTransactionQueries.Transaction transferTx = new AdminFinancialTransactionQueries.Transaction(
                txId,
                "TRF-001",
                AdminFinancialTransactionQueries.Type.INTERNAL_TRANSFER,
                new VndAmount(50_000L),
                now,
                List.of(
                        new AdminFinancialTransactionQueries.Entry(new AccountNumber("ACC1"), -50_000L),
                        new AdminFinancialTransactionQueries.Entry(new AccountNumber("ACC2"), 50_000L)
                )
        );

        AdminFinancialTransactionQueries.Transaction allocationTx = new AdminFinancialTransactionQueries.Transaction(
                UUID.randomUUID(),
                "ALC-001",
                AdminFinancialTransactionQueries.Type.DEMO_ALLOCATION,
                new VndAmount(1_000_000L),
                now,
                List.of(
                        new AdminFinancialTransactionQueries.Entry(new AccountNumber("TREASURY"), -1_000_000L),
                        new AdminFinancialTransactionQueries.Entry(new AccountNumber("ACC1"), 1_000_000L)
                )
        );

        when(adminFinancialTransactionQueries.listCommitted()).thenReturn(
                new AdminFinancialTransactionQueries.Transactions(List.of(transferTx, allocationTx))
        );

        List<AdminFinancialTransactionView> result = service.getFinancialTransactions();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).reference()).isEqualTo("TRF-001");
        assertThat(result.get(0).type()).isEqualTo("INTERNAL_TRANSFER");
        assertThat(result.get(0).amountVnd()).isEqualTo("50000");
        assertThat(result.get(0).entries()).hasSize(2);
        assertThat(result.get(1).reference()).isEqualTo("ALC-001");
        assertThat(result.get(1).type()).isEqualTo("DEMO_ALLOCATION");
        assertThat(result.get(1).amountVnd()).isEqualTo("1000000");
    }

    @Test
    @DisplayName("Accurate mapping of Audit Events")
    void getAuditEventsMapsAllFieldsAccurately() {
        Instant now = Instant.now();
        UUID adminId = UUID.randomUUID();

        AdminAuditRecordView record = new AdminAuditRecordView(
                now,
                adminId,
                "DEMO_ALLOCATION",
                "ACC0012345",
                "Initial allocation",
                "SUCCESS",
                "REF-001",
                null,
                null
        );

        when(adminAuditQueries.listAll()).thenReturn(List.of(record));

        List<AdminAuditRecordView> result = service.getAuditEvents();

        assertThat(result).hasSize(1);
        AdminAuditRecordView view = result.getFirst();
        assertThat(view.administratorId()).isEqualTo(adminId);
        assertThat(view.action()).isEqualTo("DEMO_ALLOCATION");
        assertThat(view.target()).isEqualTo("ACC0012345");
        assertThat(view.outcome()).isEqualTo("SUCCESS");
        assertThat(view.reference()).isEqualTo("REF-001");
        assertThat(view.beforeStatus()).isNull();
        assertThat(view.afterStatus()).isNull();
    }
}

