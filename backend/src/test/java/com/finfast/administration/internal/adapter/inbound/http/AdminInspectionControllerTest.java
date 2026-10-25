package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AdminAccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.administration.internal.application.dto.AdminAuditRecordView;
import com.finfast.administration.internal.application.port.outbound.AdminAuditQueries;
import com.finfast.administration.internal.config.AdminInspectionConfiguration;
import com.finfast.common.error.ApplicationException;
import com.finfast.common.error.ErrorCode;
import com.finfast.common.error.GlobalExceptionHandler;
import com.finfast.common.money.VndAmount;
import com.finfast.customer.api.CustomerQueries;
import com.finfast.identity.api.CurrentIdentity;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real controller/config/application wiring and Jackson; test-only providers, no session/DB E2E. */
@WebMvcTest(value = AdminInspectionController.class,
        properties = "finfast.administration.inspection.enabled=true")
@Import({AdminInspectionConfiguration.class, GlobalExceptionHandler.class})
class AdminInspectionControllerTest {
    private static final Instant TIME = Instant.parse("2026-10-25T01:02:03.123456Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private CurrentIdentity identity;
    @MockitoBean private AdminAccountQueries accounts;
    @MockitoBean private AdminFinancialTransactionQueries transactions;
    @MockitoBean private AdminAuditQueries audits;
    @MockitoBean private CustomerQueries customers;

    @Test
    void getAccountsReturnsAllValidAccounts() throws Exception {
        authorize();
        var active = account("ACC1", CustomerAccount.Status.ACTIVE, 0);
        var frozen = account("TREASURY-CUSTOMER", CustomerAccount.Status.FROZEN, Long.MAX_VALUE);
        when(accounts.listCustomers()).thenReturn(new AdminAccountQueries.Listing(List.of(active, frozen)));
        profile(active, "Nguyễn Văn A");
        profile(frozen, "Nguyễn Văn B");
        var items = success("accounts");
        assertThat(items.size()).isEqualTo(2);
        for (var row : items) {
            fields(row, "accountNumber", "customerId", "displayName", "status", "balanceVnd");
            assertThat(row.has("accountId")).isFalse();
            assertThat(row.get("balanceVnd").isString()).isTrue();
        }
        var first = items.get(0);
        assertThat(first.get("accountNumber").asString()).isEqualTo("ACC1");
        assertThat(first.get("customerId").asString()).isEqualTo(active.customerId().toString());
        assertThat(first.get("displayName").asString()).isEqualTo("Nguyễn Văn A");
        assertThat(first.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(first.get("balanceVnd").asString()).isEqualTo("0");
        var second = items.get(1);
        assertThat(second.get("accountNumber").asString()).isEqualTo("TREASURY-CUSTOMER");
        assertThat(second.get("customerId").asString()).isEqualTo(frozen.customerId().toString());
        assertThat(second.get("displayName").asString()).isEqualTo("Nguyễn Văn B");
        assertThat(second.get("status").asString()).isEqualTo("FROZEN");
        assertThat(second.get("balanceVnd").asString()).isEqualTo(Long.toString(Long.MAX_VALUE));
        var order = inOrder(identity, accounts, customers);
        order.verify(identity).requireAdmin();
        order.verify(accounts).listCustomers();
        order.verify(customers).findByCustomerId(active.customerId());
        order.verify(customers).findByCustomerId(frozen.customerId());
    }

    @Test
    void getFinancialTransactionsReturnsOkWithContractCompliance() throws Exception {
        authorize();
        var transfer = transaction("TRF-001", AdminFinancialTransactionQueries.Type.INTERNAL_TRANSFER, 150_000, "ACC1", "ACC2");
        var allocation = transaction("ALC-001", AdminFinancialTransactionQueries.Type.DEMO_ALLOCATION, 1_000_000, "TREASURY", "ACC1");
        var genesis = transaction("GEN-001", AdminFinancialTransactionQueries.Type.GENESIS, Long.MAX_VALUE, "TREASURY", null);
        when(transactions.listCommitted()).thenReturn(
                new AdminFinancialTransactionQueries.Transactions(List.of(transfer, allocation, genesis)));
        var items = success("financial-transactions");
        assertThat(items.size()).isEqualTo(3);
        var expected = List.of(transfer, allocation, genesis);
        for (int i = 0; i < expected.size(); i++) {
            var row = items.get(i);
            var fact = expected.get(i);
            fields(row, "reference", "type", "amountVnd", "currency", "committedAt", "entries");
            assertThat(row.has("transactionId")).isFalse();
            assertThat(row.get("reference").asString()).isEqualTo(fact.reference());
            assertThat(row.get("type").asString()).isEqualTo(fact.type().name());
            assertThat(row.get("amountVnd").isString()).isTrue();
            assertThat(row.get("amountVnd").asString()).isEqualTo(Long.toString(fact.amount().value()));
            assertThat(row.get("currency").asString()).isEqualTo("VND");
            assertThat(row.get("committedAt").asString()).isEqualTo(TIME.toString());
            assertThat(row.get("entries").size()).isEqualTo(fact.entries().size());
            for (int j = 0; j < fact.entries().size(); j++) {
                var entry = row.get("entries").get(j);
                fields(entry, "accountNumber", "deltaVnd");
                assertThat(entry.get("accountNumber").asString()).isEqualTo(fact.entries().get(j).accountNumber().value());
                assertThat(entry.get("deltaVnd").isString()).isTrue();
                assertThat(entry.get("deltaVnd").asString()).isEqualTo(Long.toString(fact.entries().get(j).deltaVnd()));
            }
        }
        var order = inOrder(identity, transactions);
        order.verify(identity).requireAdmin();
        order.verify(transactions).listCommitted();
    }

    @Test
    void getAuditEventsSerializesNullOptionalFieldsCorrectly() throws Exception {
        authorize();
        var adminId = UUID.randomUUID();
        var allocation = new AdminAuditRecordView(TIME, adminId, "DEMO_ALLOCATION", "ACC1", " seed ", "SUCCESS", "ALC-001", null, null);
        var freeze = new AdminAuditRecordView(TIME, adminId, "FREEZE_ACCOUNT", "ACC1", "freeze", "SUCCESS", null, "ACTIVE", "FROZEN");
        var unfreeze = new AdminAuditRecordView(TIME, adminId, "UNFREEZE_ACCOUNT", "ACC1", "resume", "SUCCESS", null, "FROZEN", "ACTIVE");
        var events = List.of(allocation, freeze, unfreeze);
        when(audits.listAll()).thenReturn(events);
        var items = success("audit-events");
        assertThat(items.size()).isEqualTo(3);
        for (int i = 0; i < events.size(); i++) {
            var event = events.get(i);
            var row = items.get(i);
            assertThat(row.get("timestamp").asString()).isEqualTo(TIME.toString());
            assertThat(row.get("administratorId").asString()).isEqualTo(adminId.toString());
            assertThat(row.get("action").asString()).isEqualTo(event.action());
            assertThat(row.get("target").asString()).isEqualTo(event.target());
            assertThat(row.get("reason").asString()).isEqualTo(event.reason());
            assertThat(row.get("outcome").asString()).isEqualTo("SUCCESS");
            assertThat(row.has("reference")).isEqualTo(event.reference() != null);
            assertThat(row.has("beforeStatus")).isEqualTo(event.beforeStatus() != null);
            assertThat(row.has("afterStatus")).isEqualTo(event.afterStatus() != null);
            if (i == 0) {
                fields(row, "timestamp", "administratorId", "action", "target", "reason", "outcome", "reference");
                assertThat(row.get("reference").asString()).isEqualTo("ALC-001");
            } else {
                fields(row, "timestamp", "administratorId", "action", "target", "reason", "outcome", "beforeStatus", "afterStatus");
                assertThat(row.get("beforeStatus").asString()).isEqualTo(event.beforeStatus());
                assertThat(row.get("afterStatus").asString()).isEqualTo(event.afterStatus());
            }
        }
        var order = inOrder(identity, audits);
        order.verify(identity).requireAdmin();
        order.verify(audits).listAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "financial-transactions", "audit-events"})
    void unauthorizedRequestWithTypedExceptionReturns401(String path) throws Exception {
        when(identity.requireAdmin()).thenThrow(new ApplicationException(TestAuthError.UNAUTHORIZED,
                new IllegalStateException("synthetic-secret")));
        failure(path, 401, "UNAUTHORIZED", "Authentication required.");
        verifyNoInteractions(accounts, transactions, audits, customers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "financial-transactions", "audit-events"})
    void forbiddenRequestWithTypedExceptionReturns403(String path) throws Exception {
        when(identity.requireAdmin()).thenThrow(new ApplicationException(TestAuthError.FORBIDDEN));
        failure(path, 403, "FORBIDDEN", "Access denied.");
        verifyNoInteractions(accounts, transactions, audits, customers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "financial-transactions", "audit-events"})
    void genericExceptionReturns500WithoutLeakingDetails(String path) throws Exception {
        when(identity.requireAdmin()).thenThrow(new IllegalStateException("synthetic-secret at jdbc:postgresql://private"));
        failure(path, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
        verifyNoInteractions(accounts, transactions, audits, customers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "financial-transactions", "audit-events"})
    void providerFailureKeepsGlobalInfrastructureMapping(String path) throws Exception {
        authorize();
        providerFailure(path, new DataAccessResourceFailureException("synthetic-secret jdbc:postgresql://private"));
        failure(path, 503, "SERVICE_UNAVAILABLE", "The service is temporarily unavailable.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "financial-transactions", "audit-events"})
    void unexpectedProviderFailureIsNotAnAuthorizationFailure(String path) throws Exception {
        authorize();
        providerFailure(path, new SecurityException("synthetic-secret internal provider permission failure"));
        failure(path, 500, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    @Test
    void customerQueriesFailurePropagatesAs500() throws Exception {
        authorize();
        var account = account("ACC1", CustomerAccount.Status.ACTIVE, 1);
        when(accounts.listCustomers()).thenReturn(new AdminAccountQueries.Listing(List.of(account)));
        when(customers.findByCustomerId(account.customerId())).thenThrow(new IllegalStateException("synthetic-secret"));
        failure("accounts", 500, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    @Test
    void missingProfileDoesNotReturnAFabricatedSuccess() throws Exception {
        authorize();
        var account = account("ACC1", CustomerAccount.Status.ACTIVE, 1);
        when(accounts.listCustomers()).thenReturn(new AdminAccountQueries.Listing(List.of(account)));
        when(customers.findByCustomerId(account.customerId())).thenReturn(Optional.empty());
        failure("accounts", 500, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    private JsonNode success(String path) throws Exception {
        var result = mvc.perform(get("/api/v1/admin/" + path)).andExpect(status().isOk()).andReturn();
        var root = json.readTree(result.getResponse().getContentAsString());
        fields(root, "items");
        assertThat(root.get("items").isArray()).isTrue();
        return root.get("items");
    }

    private void failure(String path, int status, String code, String message) throws Exception {
        var result = mvc.perform(get("/api/v1/admin/" + path)).andExpect(status().is(status)).andReturn();
        var body = result.getResponse().getContentAsString();
        var root = json.readTree(body);
        fields(root, "code", "message");
        assertThat(root.get("code").asString()).isEqualTo(code);
        assertThat(root.get("message").asString()).isEqualTo(message);
        assertThat(body).doesNotContain("synthetic-secret", "jdbc:", "Exception", "stackTrace", "REJECTED");
    }

    private static void fields(JsonNode node, String... names) {
        assertThat(node.propertyNames()).containsExactlyInAnyOrder(names);
    }

    private void authorize() {
        when(identity.requireAdmin()).thenReturn(new CurrentIdentity.AdminActor(UUID.randomUUID()));
    }

    private void profile(CustomerAccount account, String name) {
        when(customers.findByCustomerId(account.customerId())).thenReturn(Optional.of(
                new CustomerQueries.Profile(account.customerId(), UUID.randomUUID(), name)));
    }

    private void providerFailure(String path, RuntimeException failure) {
        switch (path) {
            case "accounts" -> when(accounts.listCustomers()).thenThrow(failure);
            case "financial-transactions" -> when(transactions.listCommitted()).thenThrow(failure);
            case "audit-events" -> when(audits.listAll()).thenThrow(failure);
            default -> throw new AssertionError(path);
        }
    }

    private static CustomerAccount account(String number, CustomerAccount.Status status, long balance) {
        return new CustomerAccount(UUID.randomUUID(), UUID.randomUUID(), new AccountNumber(number), status, new VndAmount(balance));
    }

    private static AdminFinancialTransactionQueries.Transaction transaction(String reference,
            AdminFinancialTransactionQueries.Type type, long amount, String source, String target) {
        var entries = type == AdminFinancialTransactionQueries.Type.GENESIS
                ? List.of(new AdminFinancialTransactionQueries.Entry(new AccountNumber(source), amount))
                : List.of(new AdminFinancialTransactionQueries.Entry(new AccountNumber(source), -amount),
                        new AdminFinancialTransactionQueries.Entry(new AccountNumber(target), amount));
        return new AdminFinancialTransactionQueries.Transaction(UUID.randomUUID(), reference, type, new VndAmount(amount), TIME, entries);
    }

    // Illustrates the common error convention only; the real Identity provider is absent.
    enum TestAuthError implements ErrorCode {
        UNAUTHORIZED(Category.UNAUTHORIZED, "Authentication required."),
        FORBIDDEN(Category.FORBIDDEN, "Access denied.");
        private final Category category;
        private final String message;
        TestAuthError(Category category, String message) { this.category = category; this.message = message; }
        public String code() { return name(); }
        public String publicMessage() { return message; }
        public Category category() { return category; }
    }
}
