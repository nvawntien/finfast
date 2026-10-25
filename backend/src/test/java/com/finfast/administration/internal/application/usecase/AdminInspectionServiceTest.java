package com.finfast.administration.internal.application.usecase;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AdminAccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.administration.internal.application.dto.AdminAuditRecordView;
import com.finfast.administration.internal.application.port.inbound.AdminInspectionUseCase;
import com.finfast.administration.internal.application.port.outbound.AdminAuditQueries;
import com.finfast.common.error.ApplicationException;
import com.finfast.common.error.ErrorCode;
import com.finfast.common.money.VndAmount;
import com.finfast.customer.api.CustomerQueries;
import com.finfast.identity.api.CurrentIdentity;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Isolated application-boundary evidence; no real Identity/session or database provider. */
@ExtendWith(MockitoExtension.class)
class AdminInspectionServiceTest {
    private static final Instant TIME = Instant.parse("2026-10-25T01:02:03.123456Z");
    @Mock private CurrentIdentity identity;
    @Mock private AdminAccountQueries accounts;
    @Mock private AdminFinancialTransactionQueries transactions;
    @Mock private AdminAuditQueries audits;
    @Mock private CustomerQueries customers;
    private AdminInspectionService service;

    @BeforeEach
    void setUp() {
        service = new AdminInspectionService(identity, accounts, transactions, audits, customers);
    }

    @Test
    void getAccountsTrustsAdminAccountQueriesContract() {
        authorize();
        var active = account("ACC1234567", CustomerAccount.Status.ACTIVE, 500_000);
        var frozen = account("TREASURY-CUSTOMER", CustomerAccount.Status.FROZEN, Long.MAX_VALUE);
        when(accounts.listCustomers()).thenReturn(new AdminAccountQueries.Listing(List.of(frozen, active)));
        profile(active, "Alice Wonderland");
        profile(frozen, "  Nguyễn Văn B  ");

        var result = service.getAccounts();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().accountNumber()).isEqualTo("ACC1234567");
        assertThat(result.getFirst().customerId()).isEqualTo(active.customerId());
        assertThat(result.getFirst().displayName()).isEqualTo("Alice Wonderland");
        assertThat(result.getFirst().status()).isEqualTo("ACTIVE");
        assertThat(result.getFirst().balanceVnd()).isEqualTo("500000");
        assertThat(result.getLast().accountNumber()).isEqualTo("TREASURY-CUSTOMER");
        assertThat(result.getLast().displayName()).isEqualTo("  Nguyễn Văn B  ");
        assertThat(result.getLast().status()).isEqualTo("FROZEN");
        assertThat(result.getLast().balanceVnd()).isEqualTo(Long.toString(Long.MAX_VALUE));
        var order = inOrder(identity, accounts, customers);
        order.verify(identity).requireAdmin();
        order.verify(accounts).listCustomers();
        order.verify(customers).findByCustomerId(active.customerId());
        order.verify(customers).findByCustomerId(frozen.customerId());
        assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyAccountsIsLegitimateWithoutCustomerLookup() {
        authorize();
        when(accounts.listCustomers()).thenReturn(new AdminAccountQueries.Listing(List.of()));
        assertThat(service.getAccounts()).isEmpty();
        verifyNoInteractions(customers);
    }

    @Test
    void missingProfileFailsInsteadOfFabricatingAName() {
        authorize();
        var account = oneAccount();
        when(customers.findByCustomerId(account.customerId())).thenReturn(Optional.empty());
        assertThatThrownBy(service::getAccounts).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u2003"})
    void blankProfileNameFails(String name) {
        authorize();
        var account = oneAccount();
        profile(account, name);
        assertThatThrownBy(service::getAccounts).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void inconsistentReturnedIdentityFails() {
        authorize();
        var account = oneAccount();
        when(customers.findByCustomerId(account.customerId())).thenReturn(Optional.of(
                new CustomerQueries.Profile(UUID.randomUUID(), UUID.randomUUID(), "Wrong Customer")));
        assertThatThrownBy(service::getAccounts).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getAccountsPropagatesCustomerQueriesFailures() {
        authorize();
        var account = oneAccount();
        var failure = new IllegalStateException("synthetic provider failure");
        when(customers.findByCustomerId(account.customerId())).thenThrow(failure);
        assertThatThrownBy(service::getAccounts).isSameAs(failure);
    }

    @Test
    void requiredDependenciesCannotBeMissing() {
        assertThatThrownBy(() -> new AdminInspectionService(null, accounts, transactions, audits, customers))
                .isInstanceOf(NullPointerException.class).hasMessage("currentIdentity");
        assertThatThrownBy(() -> new AdminInspectionService(identity, null, transactions, audits, customers))
                .isInstanceOf(NullPointerException.class).hasMessage("adminAccountQueries");
        assertThatThrownBy(() -> new AdminInspectionService(identity, accounts, null, audits, customers))
                .isInstanceOf(NullPointerException.class).hasMessage("adminFinancialTransactionQueries");
        assertThatThrownBy(() -> new AdminInspectionService(identity, accounts, transactions, null, customers))
                .isInstanceOf(NullPointerException.class).hasMessage("adminAuditQueries");
        assertThatThrownBy(() -> new AdminInspectionService(identity, accounts, transactions, audits, null))
                .isInstanceOf(NullPointerException.class).hasMessage("customerQueries");
    }

    @ParameterizedTest
    @EnumSource(Read.class)
    void directUseCaseInvocationRequiresAuthentication(Read read) {
        assertDenied(read, ErrorCode.Category.UNAUTHORIZED);
    }

    @ParameterizedTest
    @EnumSource(Read.class)
    void directUseCaseInvocationRequiresAdminRole(Read read) {
        assertDenied(read, ErrorCode.Category.FORBIDDEN);
    }

    private void assertDenied(Read read, ErrorCode.Category category) {
        // Illustrative common error convention; real Identity error integration is pending.
        var failure = new ApplicationException(new ErrorCode() {
            public String code() { return category.name(); }
            public String publicMessage() { return "Access denied."; }
            public Category category() { return category; }
        });
        when(identity.requireAdmin()).thenThrow(failure);
        assertThatThrownBy(() -> read.invoke.apply(service)).isSameAs(failure);
        verifyNoInteractions(accounts, transactions, audits, customers);
    }

    @ParameterizedTest
    @EnumSource(Read.class)
    void identityTechnicalFailurePropagatesBeforeReads(Read read) {
        var failure = new IllegalStateException("synthetic identity storage error");
        when(identity.requireAdmin()).thenThrow(failure);
        assertThatThrownBy(() -> read.invoke.apply(service)).isSameAs(failure);
        verifyNoInteractions(accounts, transactions, audits, customers);
    }

    @ParameterizedTest
    @EnumSource(Read.class)
    void missingTrustedActorFailsClosed(Read read) {
        assertThatThrownBy(() -> read.invoke.apply(service)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(accounts, transactions, audits, customers);
    }

    @Test
    void getFinancialTransactionsMapsAllTypesAccurately() {
        authorize();
        var transfer = transaction(AdminFinancialTransactionQueries.Type.INTERNAL_TRANSFER, "TRF-001", 50_000);
        var allocation = transaction(AdminFinancialTransactionQueries.Type.DEMO_ALLOCATION, "ALC-001", 1_000_000);
        var genesis = transaction(AdminFinancialTransactionQueries.Type.GENESIS, "GEN-001", Long.MAX_VALUE);
        when(transactions.listCommitted()).thenReturn(
                new AdminFinancialTransactionQueries.Transactions(List.of(transfer, allocation, genesis)));
        var result = service.getFinancialTransactions();
        assertThat(result).extracting(v -> v.reference()).containsExactly("TRF-001", "ALC-001", "GEN-001");
        assertThat(result).extracting(v -> v.type()).containsExactly("INTERNAL_TRANSFER", "DEMO_ALLOCATION", "GENESIS");
        assertThat(result).extracting(v -> v.amountVnd()).containsExactly("50000", "1000000", Long.toString(Long.MAX_VALUE));
        assertThat(result).allSatisfy(v -> {
            assertThat(v.currency()).isEqualTo("VND");
            assertThat(v.committedAt()).isEqualTo(TIME);
        });
        assertThat(result.getFirst().entries()).extracting(e -> e.deltaVnd()).containsExactly("-50000", "50000");
        assertThat(result.get(1).entries()).extracting(e -> e.deltaVnd()).containsExactly("-1000000", "1000000");
        assertThat(result.getLast().entries()).extracting(e -> e.deltaVnd()).containsExactly(Long.toString(Long.MAX_VALUE));
        var order = inOrder(identity, transactions);
        order.verify(identity).requireAdmin();
        order.verify(transactions).listCommitted();
    }

    @Test
    void getAuditEventsMapsAllFieldsAccurately() {
        authorize();
        var admin = UUID.randomUUID();
        var allocation = new AdminAuditRecordView(TIME, admin, "DEMO_ALLOCATION", "ACC1", " seed ", "SUCCESS", "ALC-1", null, null);
        var freeze = new AdminAuditRecordView(TIME, admin, "FREEZE_ACCOUNT", "ACC1", "freeze", "SUCCESS", null, "ACTIVE", "FROZEN");
        var unfreeze = new AdminAuditRecordView(TIME, admin, "UNFREEZE_ACCOUNT", "ACC1", "resume", "SUCCESS", null, "FROZEN", "ACTIVE");
        var source = new ArrayList<>(List.of(allocation, freeze, unfreeze));
        when(audits.listAll()).thenReturn(source);
        var result = service.getAuditEvents();
        source.clear();
        assertThat(result).containsExactly(allocation, freeze, unfreeze);
        assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
        var order = inOrder(identity, audits);
        order.verify(identity).requireAdmin();
        order.verify(audits).listAll();
    }

    @ParameterizedTest
    @EnumSource(Read.class)
    void providerTechnicalFailuresPropagate(Read read) {
        authorize();
        var failure = new IllegalStateException("synthetic provider error");
        switch (read) {
            case ACCOUNTS -> when(accounts.listCustomers()).thenThrow(failure);
            case TRANSACTIONS -> when(transactions.listCommitted()).thenThrow(failure);
            case AUDITS -> when(audits.listAll()).thenThrow(failure);
        }
        assertThatThrownBy(() -> read.invoke.apply(service)).isSameAs(failure);
    }

    @Test
    void nullAuditResultIsNotConvertedToAnEmptySuccess() {
        authorize();
        when(audits.listAll()).thenReturn(null);
        assertThatThrownBy(service::getAuditEvents).isInstanceOf(NullPointerException.class);
    }

    private void authorize() {
        when(identity.requireAdmin()).thenReturn(new CurrentIdentity.AdminActor(UUID.randomUUID()));
    }

    private CustomerAccount oneAccount() {
        var account = account("ACC1", CustomerAccount.Status.ACTIVE, 0);
        when(accounts.listCustomers()).thenReturn(new AdminAccountQueries.Listing(List.of(account)));
        return account;
    }

    private void profile(CustomerAccount account, String name) {
        when(customers.findByCustomerId(account.customerId())).thenReturn(Optional.of(
                new CustomerQueries.Profile(account.customerId(), UUID.randomUUID(), name)));
    }

    private static CustomerAccount account(String number, CustomerAccount.Status status, long balance) {
        return new CustomerAccount(UUID.randomUUID(), UUID.randomUUID(), new AccountNumber(number), status, new VndAmount(balance));
    }

    private static AdminFinancialTransactionQueries.Transaction transaction(
            AdminFinancialTransactionQueries.Type type, String reference, long amount) {
        var entries = type == AdminFinancialTransactionQueries.Type.GENESIS
                ? List.of(new AdminFinancialTransactionQueries.Entry(new AccountNumber("TREASURY"), amount))
                : List.of(new AdminFinancialTransactionQueries.Entry(new AccountNumber("ACC1"), -amount),
                        new AdminFinancialTransactionQueries.Entry(new AccountNumber("ACC2"), amount));
        return new AdminFinancialTransactionQueries.Transaction(UUID.randomUUID(), reference, type, new VndAmount(amount), TIME, entries);
    }

    enum Read {
        ACCOUNTS(AdminInspectionUseCase::getAccounts),
        TRANSACTIONS(AdminInspectionUseCase::getFinancialTransactions),
        AUDITS(AdminInspectionUseCase::getAuditEvents);
        final Function<AdminInspectionUseCase, ?> invoke;
        Read(Function<AdminInspectionUseCase, ?> invoke) { this.invoke = invoke; }
    }
}
