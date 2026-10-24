package com.finfast.administration.internal.application.service;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AdminAccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.administration.internal.application.dto.AdminAccountView;
import com.finfast.administration.internal.application.dto.AdminAuditRecordView;
import com.finfast.administration.internal.application.dto.AdminFinancialTransactionView;
import com.finfast.administration.internal.application.port.outbound.AdminAuditQueries;
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
    @DisplayName("Accounts list explicitly excludes Treasury account")
    void getAccountsExplicitlyExcludesTreasuryAccount() {
        UUID customerId = UUID.randomUUID();
        CustomerAccount customerAccount = new CustomerAccount(
                UUID.randomUUID(),
                customerId,
                new AccountNumber("ACC1234567"),
                CustomerAccount.Status.ACTIVE,
                new VndAmount(500_000L)
        );

        CustomerAccount treasuryAccount = new CustomerAccount(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new AccountNumber("TREASURY"),
                CustomerAccount.Status.ACTIVE,
                new VndAmount(100_000_000L)
        );

        when(adminAccountQueries.listCustomers()).thenReturn(
                new AdminAccountQueries.Listing(List.of(customerAccount, treasuryAccount))
        );
        when(customerQueries.findByCustomerId(customerId)).thenReturn(
                Optional.of(new CustomerQueries.Profile(customerId, UUID.randomUUID(), "Alice Wonderland"))
        );

        List<AdminAccountView> result = service.getAccounts();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().accountNumber()).isEqualTo("ACC1234567");
        assertThat(result).noneMatch(acc -> acc.accountNumber().equalsIgnoreCase("TREASURY"));
    }

    @Test
    @DisplayName("Accounts list properly extracts display name from CustomerQueries and falls back safely")
    void getAccountsExtractsDisplayNameProperly() {
        UUID customerId1 = UUID.randomUUID();
        UUID customerId2 = UUID.randomUUID();

        CustomerAccount account1 = new CustomerAccount(
                UUID.randomUUID(),
                customerId1,
                new AccountNumber("ACC1000001"),
                CustomerAccount.Status.ACTIVE,
                new VndAmount(100_000L)
        );

        CustomerAccount account2 = new CustomerAccount(
                UUID.randomUUID(),
                customerId2,
                new AccountNumber("ACC1000002"),
                CustomerAccount.Status.FROZEN,
                new VndAmount(200_000L)
        );

        when(adminAccountQueries.listCustomers()).thenReturn(
                new AdminAccountQueries.Listing(List.of(account1, account2))
        );
        when(customerQueries.findByCustomerId(customerId1)).thenReturn(
                Optional.of(new CustomerQueries.Profile(customerId1, UUID.randomUUID(), "Bob Smith"))
        );
        when(customerQueries.findByCustomerId(customerId2)).thenReturn(Optional.empty());

        List<AdminAccountView> result = service.getAccounts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).displayName()).isEqualTo("Bob Smith");
        // Fallback must not use customerId UUID
        assertThat(result.get(1).displayName()).isEqualTo("Customer ACC1000002");
        assertThat(result.get(1).displayName()).doesNotContain(customerId2.toString());
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

        AdminFinancialTransactionQueries.Transaction genesisTx = new AdminFinancialTransactionQueries.Transaction(
                UUID.randomUUID(),
                "GENESIS-001",
                AdminFinancialTransactionQueries.Type.GENESIS,
                new VndAmount(100_000_000L),
                now,
                List.of(
                        new AdminFinancialTransactionQueries.Entry(new AccountNumber("TREASURY"), 100_000_000L)
                )
        );

        when(adminFinancialTransactionQueries.listCommitted()).thenReturn(
                new AdminFinancialTransactionQueries.Transactions(List.of(transferTx, allocationTx, genesisTx))
        );

        List<AdminFinancialTransactionView> result = service.getFinancialTransactions();

        assertThat(result).hasSize(3);

        AdminFinancialTransactionView mappedTransfer = result.get(0);
        assertThat(mappedTransfer.reference()).isEqualTo("TRF-001");
        assertThat(mappedTransfer.type()).isEqualTo("INTERNAL_TRANSFER");
        assertThat(mappedTransfer.amountVnd()).isEqualTo("50000");
        assertThat(mappedTransfer.currency()).isEqualTo("VND");
        assertThat(mappedTransfer.entries()).hasSize(2);
        assertThat(mappedTransfer.entries().get(0).deltaVnd()).isEqualTo("-50000");
        assertThat(mappedTransfer.entries().get(1).deltaVnd()).isEqualTo("50000");

        AdminFinancialTransactionView mappedAllocation = result.get(1);
        assertThat(mappedAllocation.type()).isEqualTo("DEMO_ALLOCATION");
        assertThat(mappedAllocation.amountVnd()).isEqualTo("1000000");

        AdminFinancialTransactionView mappedGenesis = result.get(2);
        assertThat(mappedGenesis.type()).isEqualTo("GENESIS");
        assertThat(mappedGenesis.amountVnd()).isEqualTo("100000000");
        assertThat(mappedGenesis.entries()).hasSize(1);
        assertThat(mappedGenesis.entries().getFirst().deltaVnd()).isEqualTo("100000000");
    }

    @Test
    @DisplayName("Complete mapping of Audit Events: admin, target, reason, before/after status")
    void getAuditEventsMapsAllFieldsAccurately() {
        Instant timestamp = Instant.now();
        UUID adminId = UUID.randomUUID();

        AdminAuditRecordView auditRecord = new AdminAuditRecordView(
                timestamp,
                adminId,
                "FREEZE_ACCOUNT",
                "ACC1234567",
                "Suspicious transfer activity detected",
                "SUCCESS",
                "REF-AUDIT-999",
                "ACTIVE",
                "FROZEN"
        );

        when(adminAuditQueries.listAll()).thenReturn(List.of(auditRecord));

        List<AdminAuditRecordView> result = service.getAuditEvents();

        assertThat(result).hasSize(1);
        AdminAuditRecordView mapped = result.getFirst();
        assertThat(mapped.administratorId()).isEqualTo(adminId);
        assertThat(mapped.target()).isEqualTo("ACC1234567");
        assertThat(mapped.action()).isEqualTo("FREEZE_ACCOUNT");
        assertThat(mapped.reason()).isEqualTo("Suspicious transfer activity detected");
        assertThat(mapped.outcome()).isEqualTo("SUCCESS");
        assertThat(mapped.reference()).isEqualTo("REF-AUDIT-999");
        assertThat(mapped.beforeStatus()).isEqualTo("ACTIVE");
        assertThat(mapped.afterStatus()).isEqualTo("FROZEN");
        assertThat(mapped.timestamp()).isEqualTo(timestamp);
    }
}
