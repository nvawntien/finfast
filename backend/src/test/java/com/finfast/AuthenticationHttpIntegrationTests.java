package com.finfast;

import com.finfast.identity.api.CurrentIdentity;
import com.finfast.identity.internal.application.dto.Credential;
import com.finfast.identity.internal.adapter.outbound.persistence.JdbcLoginCredentials;
import com.finfast.identity.internal.application.port.outbound.LoginCredentials;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Real embedded servlet server/filter chain/session cookies and isolated PostgreSQL, synthetic users only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"finfast.auth.require-https=false", "finfast.auth.bcrypt-strength=4"})
@ActiveProfiles("auth")
@Tag("integration")
@Import({PostgresIntegrationTests.ContainerConfiguration.class, AuthenticationHttpIntegrationTests.Fixtures.class})
@Timeout(45)
class AuthenticationHttpIntegrationTests {
    private static final JsonMapper JSON = new JsonMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final String PASSWORD = "synthetic-password";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    private UUID user;
    private UUID customer;
    private String email;

    @BeforeEach void syntheticUser() {
        user = UUID.randomUUID(); customer = UUID.randomUUID(); email = user + "@example.test";
        jdbc.update("INSERT INTO app_user (id, email_normalized, password_hash, role) VALUES (?, ?, ?, 'CUSTOMER')",
                user, email, new BCryptPasswordEncoder(4).encode(PASSWORD));
        jdbc.update("INSERT INTO customer (id, user_id, display_name) VALUES (?, ?, 'Synthetic Alice')", customer, user);
    }
    private HttpResponse<String> request(String method, String path, String cookie, String token, String body, String origin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (cookie != null) { builder.header("Cookie", cookie); }
        if (token != null) { builder.header("X-CSRF-TOKEN", token); }
        if (origin != null) { builder.header("Origin", origin); }
        if (body != null) { builder.header("Content-Type", "application/json"); }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, String cookie) throws Exception { return request("GET", path, cookie, null, null, null); }
    private Browser csrf(String cookie) throws Exception {
        var response = get("/api/v1/auth/csrf", cookie);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).size()).isEqualTo(1);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        return new Browser(cookie(response, cookie), JSON.readTree(response.body()).get("token").asString());
    }
    private String cookie(HttpResponse<String> response, String fallback) {
        return response.headers().allValues("Set-Cookie").stream().filter(value -> value.startsWith("FINFAST_SESSION="))
                .map(value -> value.split(";", 2)[0]).findFirst().orElse(fallback);
    }
    private String loginBody(String name, String password) { return JSON.writeValueAsString(Map.of("email", name, "password", password)); }
    private HttpResponse<String> login(Browser browser, String name, String password) throws Exception {
        return request("POST", "/api/v1/auth/login", browser.cookie(), browser.token(), loginBody(name, password), null);
    }
    private String signedIn() throws Exception {
        Browser browser = csrf(null);
        var response = login(browser, email, PASSWORD);
        assertThat(response.statusCode()).isEqualTo(200);
        return cookie(response, null);
    }
    private record Browser(String cookie, String token) { }

    @Autowired Probe probe;

    @Test void publicRegistrationRequiresHeaderCsrfAndSameOriginBeforeInvokingAdapter() throws Exception {
        int before = probe.registrations.get();
        var browser = csrf(null);
        var other = csrf(null);
        assertThat(request("POST", "/api/v1/auth/register", browser.cookie(), null, "{}", null).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/v1/auth/register", browser.cookie(), "invalid", "{}", null).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/v1/auth/register", browser.cookie(), other.token(), "{}", null).statusCode()).isEqualTo(403);
        String encoded = java.net.URLEncoder.encode(browser.token(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(request("POST", "/api/v1/auth/register?_csrf=" + encoded, browser.cookie(), null, "{}", null).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/v1/auth/register", browser.cookie(), browser.token(), "{}", "https://foreign.example.test").statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/auth/register", browser.cookie()).statusCode()).isEqualTo(401);
        assertThat(probe.registrations.get()).isEqualTo(before);
        assertThat(request("POST", "/api/v1/auth/register", browser.cookie(), browser.token(), "{}",
                "http://127.0.0.1:" + port).statusCode()).isEqualTo(200);
        assertThat(probe.registrations.get()).isEqualTo(before + 1);
        assertThat(get("/api/v1/auth/session", browser.cookie()).statusCode()).isEqualTo(401);
    }

    @Test void loginRotatesSessionAndCsrfAndExposesOnlyMinimalIdentity() throws Exception {
        var before = csrf(null);
        var response = login(before, email.toUpperCase(java.util.Locale.ROOT), PASSWORD);
        assertThat(response.statusCode()).isEqualTo(200);
        String after = cookie(response, null);
        assertThat(after).isNotNull().isNotEqualTo(before.cookie());
        assertThat(response.headers().allValues("Set-Cookie").toString()).contains("FINFAST_SESSION=", "Secure", "HttpOnly", "SameSite=Lax");
        JsonNode identity = JSON.readTree(response.body());
        assertThat(identity.size()).isEqualTo(3);
        assertThat(identity.get("userId").asString()).isEqualTo(user.toString());
        assertThat(identity.get("role").asString()).isEqualTo("CUSTOMER");
        assertThat(response.body()).doesNotContain(email, PASSWORD, "password", "customerId", "$2a$");
        assertThat(get("/api/v1/auth/session", before.cookie()).statusCode()).isEqualTo(401);
        var session = get("/api/v1/auth/session", after);
        assertThat(session.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(session.body())).isEqualTo(identity);
        get("/api/v1/auth/logout", after); // GET must not mutate authentication state.
        assertThat(get("/api/v1/auth/session", after).statusCode()).isEqualTo(200);
        assertThat(request("POST", "/api/v1/auth/logout", after, before.token(), null, null).statusCode()).isEqualTo(403);
        var current = csrf(after);
        assertThat(request("POST", "/api/v1/auth/logout", after, current.token(), null, null).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/auth/session", after).statusCode()).isEqualTo(401);
    }
    @Test void invalidCredentialsAndUnknownUserGiveSameSafeResponse() throws Exception {
        var wrong = login(csrf(null), email, "wrong");
        var missing = login(csrf(null), "missing@example.test", PASSWORD);
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(missing.statusCode()).isEqualTo(401);
        assertThat(wrong.body()).isEqualTo(missing.body()).doesNotContain(email, PASSWORD);
    }
    @Test void missingWrongQueryOrForeignSessionCsrfIsRejected() throws Exception {
        var browser = csrf(null);
        var other = csrf(null);
        assertThat(request("POST", "/api/v1/auth/login", browser.cookie(), null, loginBody(email, PASSWORD), null).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/v1/auth/login", browser.cookie(), other.token(), loginBody(email, PASSWORD), null).statusCode()).isEqualTo(403);
        String encoded = java.net.URLEncoder.encode(browser.token(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(request("POST", "/api/v1/auth/login?_csrf=" + encoded, browser.cookie(), null, loginBody(email, PASSWORD), null).statusCode()).isEqualTo(403);
        assertThat(login(browser, email, PASSWORD).statusCode()).isEqualTo(200);
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"email\":12,\"password\":\"x\"}", "{\"email\":\"a\",\"password\":\"x\",\"role\":\"ADMIN\"}", "{\"email\":\"a\",\"email\":\"b\",\"password\":\"x\"}", "not-json"})
    void malformedLoginCannotAuthenticateOrSelectRole(String body) throws Exception {
        var browser = csrf(null);
        var response = request("POST", "/api/v1/auth/login", browser.cookie(), browser.token(), body, null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).doesNotContain(body);
        assertThat(get("/api/v1/auth/session", browser.cookie()).statusCode()).isEqualTo(401);
    }
    @Test void crossOriginMutationDeniedEvenWithValidToken() throws Exception {
        var browser = csrf(null);
        assertThat(request("POST", "/api/v1/auth/login", browser.cookie(), browser.token(), loginBody(email, PASSWORD), "https://foreign.example.test").statusCode()).isEqualTo(403);
        assertThat(login(browser, email, PASSWORD).statusCode()).isEqualTo(200);
    }
    @Test void trailingJsonCannotHideExtraLoginInput() throws Exception {
        var browser = csrf(null);
        assertThat(request("POST", "/api/v1/auth/login", browser.cookie(), browser.token(),
                loginBody(email, PASSWORD) + " {}", null).statusCode()).isEqualTo(401);
    }
    @Test void frozenAccountDoesNotBlockAuthenticationOrCurrentIdentity() throws Exception {
        jdbc.update("INSERT INTO account (id, account_number, customer_id, account_type, status) VALUES (?, ?, ?, 'CUSTOMER', 'FROZEN')",
                UUID.randomUUID(), UUID.randomUUID().toString().replace("-", ""), customer);
        String cookie = signedIn();
        var response = get("/api/v1/accounts/_test/current", cookie);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).get("customerId").asString()).isEqualTo(customer.toString());
        assertThat(get("/api/v1/admin/_test/current", cookie).statusCode()).isEqualTo(403);
    }
    @Test void adminSessionHasNoCustomerRoleOrCredentialFields() throws Exception {
        jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE id = ?", user); // Synthetic fixture, not a bootstrap API.
        var response = login(csrf(null), email, PASSWORD);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).size()).isEqualTo(2);
        String cookie = cookie(response, null);
        assertThat(get("/api/v1/admin/_test/current", cookie).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/accounts/_test/current", cookie).statusCode()).isEqualTo(403);
        var token = csrf(cookie);
        assertThat(request("POST", "/api/v1/request-outcomes/resolve", cookie, token.token(), "{}", null).statusCode()).isEqualTo(403);
    }
    @Test void expiredSessionIsRejectedByRealServletContainer() throws Exception {
        String cookie = signedIn();
        var browser = csrf(cookie);
        assertThat(request("POST", "/api/v1/accounts/_test/expire", cookie, browser.token(), "{}", null).statusCode()).isEqualTo(200);
        Thread.sleep(1500);
        assertThat(get("/api/v1/auth/session", cookie).statusCode()).isEqualTo(401);
    }
    @Test void failedReloginInvalidatesPreviousAuthenticatedSession() throws Exception {
        String cookie = signedIn();
        assertThat(login(csrf(cookie), email, "wrong").statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/auth/session", cookie).statusCode()).isEqualTo(401);
    }
    @Test void technicalFailureIs503AndDoesNotCreateAuthenticatedSession() throws Exception {
        var browser = csrf(null);
        var response = login(browser, "unavailable@example.test", PASSWORD);
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).doesNotContain("private", "SQL", "REJECTED");
        assertThat(get("/api/v1/auth/session", browser.cookie()).statusCode()).isEqualTo(401);
    }
    @Test void credentialUpgradeUsesCompareAndSetWithoutOverwritingNewerHash() {
        var repository = new JdbcLoginCredentials(jdbc);
        String old = repository.find(email).orElseThrow().passwordHash();
        String newer = new BCryptPasswordEncoder(5).encode(PASSWORD);
        assertThat(repository.upgradeHash(user, old, newer)).isTrue();
        assertThat(repository.upgradeHash(user, old, "stale")).isFalse();
        assertThat(repository.find(email).orElseThrow().passwordHash()).isEqualTo(newer);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(Probe.class)
    static class Fixtures {
        @Bean @Primary LoginCredentials faultAwareCredentials(JdbcTemplate jdbc) {
            var real = new JdbcLoginCredentials(jdbc);
            return new LoginCredentials() {
                @Override public Optional<Credential> find(String email) {
                    if (email.equals("unavailable@example.test")) { throw new IllegalStateException("private SQL failure"); }
                    return real.find(email);
                }
                @Override public boolean upgradeHash(UUID user, String old, String replacement) { return real.upgradeHash(user, old, replacement); }
            };
        }
    }
    @RestController @Profile("auth")
    static class Probe {
        final AtomicInteger registrations = new AtomicInteger();
        private final CurrentIdentity identity;
        Probe(CurrentIdentity identity) { this.identity = identity; }
        // Security routing probe only: no BK-005 implementation or real registration response.
        @PostMapping("/api/v1/auth/register") Object register() {
            registrations.incrementAndGet();
            return Map.of("probe", "registration-adapter-reached");
        }
        @GetMapping("/api/v1/accounts/_test/current") Object customer() { return identity.requireCustomer(); }
        @GetMapping("/api/v1/admin/_test/current") Object admin() { return identity.requireAdmin(); }
        @PostMapping("/api/v1/request-outcomes/resolve") Object wrongRole() { return identity.requireCustomer(); }
        @PostMapping("/api/v1/accounts/_test/expire") Object expire(HttpServletRequest request) {
            request.getSession(false).setMaxInactiveInterval(1);
            return Map.of("message", "Test-only short expiry");
        }
    }
}
