package com.finfast;

import com.finfast.accounts.internal.application.usecase.AccountQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Real BK-007 login, CSRF, servlet sessions and security chain; no injected Authentication or request principal. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"finfast.auth.require-https=false", "finfast.auth.bcrypt-strength=4"})
@ActiveProfiles("auth") @Tag("integration") @Timeout(45)
@Import({PostgresIntegrationTests.ContainerConfiguration.class, AccountOverviewSecurityIntegrationTests.Database.class})
class AccountOverviewSecurityIntegrationTests {
    private static final JsonMapper JSON = new JsonMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExpiringSessions sessions;
    @MockitoSpyBean AccountQueryService queries;
    private UUID user;
    private UUID customer;
    private UUID account;
    private String email;
    private String number;

    @BeforeEach void seed(TestInfo info) {
        user = UUID.randomUUID(); customer = UUID.randomUUID(); account = UUID.randomUUID();
        email = user + "@example.test"; number = "ACC-" + account.toString().replace("-", "").substring(0, 20);
        jdbc.update("INSERT INTO public.app_user(id,email_normalized,password_hash,role) VALUES (?, ?, ?, 'CUSTOMER')",
                user, email, new BCryptPasswordEncoder(4).encode("synthetic-password"));
        jdbc.update("INSERT INTO public.customer(id,user_id,display_name) VALUES (?, ?, 'Synthetic Customer')", customer, user);
        if (!info.getTestMethod().orElseThrow().getName().equals("missingAccountGivesSafeDenialAndNeverTreasury")) {
            jdbc.update("INSERT INTO public.account(id,account_number,customer_id,account_type,balance_vnd) VALUES (?, ?, ?, 'CUSTOMER', 250000)", account, number, customer);
        }
        clearInvocations(queries);
    }
    private HttpResponse<String> request(String method, String path, String cookie, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (cookie != null) { builder.header("Cookie", cookie); }
        if (token != null) { builder.header("X-CSRF-TOKEN", token); }
        if (body != null) { builder.header("Content-Type", "application/json"); }
        return HTTP.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, String cookie) throws Exception { return request("GET", path, cookie, null, null); }
    private String cookie(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream().filter(s -> s.startsWith("FINFAST_SESSION="))
                .map(s -> s.split(";", 2)[0]).findFirst().orElseThrow();
    }
    private String login() throws Exception {
        var csrf = get("/api/v1/auth/csrf", null);
        assertThat(csrf.statusCode()).isEqualTo(200);
        String token = JSON.readTree(csrf.body()).get("token").asString();
        var response = request("POST", "/api/v1/auth/login", cookie(csrf), token,
                JSON.writeValueAsString(Map.of("email", email, "password", "synthetic-password")));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).get("userId").asString()).isEqualTo(user.toString());
        assertThat(user).isNotEqualTo(customer);
        return cookie(response);
    }
    private JsonNode error(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        JsonNode json = JSON.readTree(response.body());
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get("code").asString()).isEqualTo(code);
        assertThat(json.get("message").asString()).isNotBlank();
        assertThat(response.body()).doesNotContain(number, account.toString(), customer.toString(), user.toString(), "TREASURY", "SQL", "outcome");
        return json;
    }
    @ParameterizedTest @ValueSource(strings = {"ACTIVE", "FROZEN"})
    void actualSessionPrincipalProjectsCustomerIdAndReturnsOnlyOwnedOverview(String status) throws Exception {
        jdbc.update("UPDATE public.account SET status = ? WHERE id = ?", status, account);
        String session = login();
        var response = get("/api/v1/accounts/me", session);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = JSON.readTree(response.body());
        assertThat(json.size()).isEqualTo(6);
        assertThat(json.get("accountNumber").asString()).isEqualTo(number);
        assertThat(json.get("type").asString()).isEqualTo("CUSTOMER");
        assertThat(json.get("status").asString()).isEqualTo(status);
        assertThat(json.get("currency").asString()).isEqualTo("VND");
        assertThat(json.get("balanceVnd").asString()).isEqualTo("250000");
        assertThat(json.get("availableBalanceVnd").asString()).isEqualTo("250000");
        assertThat(response.body()).doesNotContain(user.toString(), customer.toString(), account.toString(), "password", email);
        verify(queries).findByCustomerId(customer);
        verify(queries, never()).findByCustomerId(user);
    }
    @Test void missingOrInvalidSessionDeniesBeforeAccountAccess() throws Exception {
        error(get("/api/v1/accounts/me", null), 401, "UNAUTHENTICATED");
        error(get("/api/v1/accounts/me", "FINFAST_SESSION=expired-or-forged"), 401, "UNAUTHENTICATED");
        verifyNoInteractions(queries);
    }
    @Test void adminRoleCannotReadCustomerOverview() throws Exception {
        jdbc.update("UPDATE public.app_user SET role = 'ADMIN' WHERE id = ?", user);
        error(get("/api/v1/accounts/me", login()), 403, "FORBIDDEN");
        verifyNoInteractions(queries);
    }
    @Test void missingAccountGivesSafeDenialAndNeverTreasury() throws Exception {
        var fixtures = new DatabaseFixtures(jdbc, 100000);
        fixtures.account("account_type", "TREASURY", "account_number", "SECRET-TREASURY");
        var response = get("/api/v1/accounts/me", login());
        error(response, 403, "FORBIDDEN");
        assertThat(response.body()).doesNotContain("SECRET-TREASURY");
    }
    @Test void clientIdentityInputsCannotSelectAnotherAccount() throws Exception {
        var fixtures = new DatabaseFixtures(jdbc, 200000);
        UUID other = fixtures.customer();
        fixtures.account("customer_id", other, "account_number", "OTHER-SECRET");
        var response = get("/api/v1/accounts/me?customerId=" + other + "&accountNumber=OTHER-SECRET&authenticatedCustomerId=" + other, login());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).get("accountNumber").asString()).isEqualTo(number);
        assertThat(response.body()).doesNotContain("OTHER-SECRET", other.toString());
        verify(queries).findByCustomerId(customer);
        verify(queries, never()).findByCustomerId(other);
    }
    @Test void invalidatedSessionCannotReadAccounts() throws Exception {
        String session = login();
        var csrf = get("/api/v1/auth/csrf", session);
        String token = JSON.readTree(csrf.body()).get("token").asString();
        assertThat(request("POST", "/api/v1/auth/logout", session, token, null).statusCode()).isEqualTo(200);
        error(get("/api/v1/accounts/me", session), 401, "UNAUTHENTICATED");
        verifyNoInteractions(queries);
    }
    @Test void genuinelyExpiredServletSessionCannotReadAccounts() throws Exception {
        String session = login();
        assertThat(sessions.current.getId()).isEqualTo(session.substring("FINFAST_SESSION=".length()));
        sessions.current.setMaxInactiveInterval(1);
        try (var scheduler = Executors.newSingleThreadScheduledExecutor()) {
            var response = scheduler.schedule(() -> get("/api/v1/accounts/me", session), 2, TimeUnit.SECONDS);
            error(response.get(6, TimeUnit.SECONDS), 401, "UNAUTHENTICATED");
        }
        verifyNoInteractions(queries);
    }
    @Test void technicalPersistenceFailureUsesSharedErrorAndCannotBecomeDenialOrEmptySuccess() throws Exception {
        String session = login();
        doThrow(new DataAccessResourceFailureException("Injected private SQL failure")).when(queries).findByCustomerId(customer);
        error(get("/api/v1/accounts/me", session), 503, "SERVICE_UNAVAILABLE");
    }
    @Test void unexpectedQueryFailureUsesSafeInternalErrorWithoutAnAuthorizationOrFinancialOutcome() throws Exception {
        String session = login();
        String privateMessage = "synthetic-unexpected-query-failure";
        String technicalSecret = "synthetic-technical-secret";
        doThrow(new IllegalStateException(privateMessage + " SQL stackTrace " + technicalSecret
                + " customer=" + customer + " account=" + account)).when(queries).findByCustomerId(customer);

        var response = get("/api/v1/accounts/me", session);
        error(response, 500, "INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain(privateMessage, technicalSecret, "stackTrace",
                "IllegalStateException", "com.finfast", "java.lang", "password", "synthetic-password", email);
        verify(queries).findByCustomerId(customer);
    }
    static class ExpiringSessions implements HttpSessionListener {
        volatile HttpSession current;
        @Override public void sessionCreated(HttpSessionEvent event) { current = event.getSession(); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean ExpiringSessions expiringSessions() { return new ExpiringSessions(); }
        @Bean ServletListenerRegistrationBean<HttpSessionListener> accountSessionListener(ExpiringSessions sessions) {
            return new ServletListenerRegistrationBean<>(sessions);
        }
        @Bean DataSource accountsHttpDataSource(PostgreSQLContainer postgres) throws SQLException {
            var database = IsolatedTestDatabase.create(postgres, "accounts_http");
            return new AbstractDataSource() {
                @Override public Connection getConnection() throws SQLException { return database.open(); }
                @Override public Connection getConnection(String user, String password) throws SQLException {
                    throw new SQLFeatureNotSupportedException("Isolated test credentials only");
                }
            };
        }
    }
}
