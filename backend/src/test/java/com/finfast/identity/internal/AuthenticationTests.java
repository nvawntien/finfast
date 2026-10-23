package com.finfast.identity.internal;

import com.finfast.customer.api.CustomerQueries;
import com.finfast.identity.internal.adapter.inbound.http.AuthTransportFilter;
import com.finfast.identity.internal.adapter.inbound.security.SessionCurrentIdentity;
import com.finfast.identity.internal.application.port.outbound.LoginCredentials;
import com.finfast.identity.internal.application.usecase.AuthenticateUser;
import com.finfast.identity.internal.application.usecase.SessionPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthenticationTests {
    private final LoginCredentials credentials = mock(LoginCredentials.class);
    private final CustomerQueries customers = mock(CustomerQueries.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UUID user = UUID.randomUUID();
    private final UUID customer = UUID.randomUUID();

    private AuthenticateUser service() { return new AuthenticateUser(credentials, customers, encoder); }
    private void stored(String role, String password) {
        when(credentials.find("alice@example.test")).thenReturn(Optional.of(new LoginCredentials.Credential(user, role, encoder.encode(password))));
        when(customers.findByUserId(user)).thenReturn(Optional.of(new CustomerQueries.Profile(customer, user, "Synthetic Alice")));
    }
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void normalizedCustomerCredentialsResolveOnlyTheirProfile() {
        stored("CUSTOMER", "synthetic-password");
        var principal = service().authenticate("ALICE@EXAMPLE.TEST", "synthetic-password");
        assertThat(principal.userId()).isEqualTo(user);
        assertThat(principal.customerId()).isEqualTo(customer);
        assertThat(principal.toString()).isEqualTo("SessionPrincipal[REDACTED]");
        verify(customers).findByUserId(user);
        verifyNoMoreInteractions(customers);
    }
    @Test void adminDoesNotRequireOrAccessCustomerProfile() {
        stored("ADMIN", "synthetic-password");
        assertThat(service().authenticate("alice@example.test", "synthetic-password").role()).isEqualTo("ADMIN");
        verifyNoInteractions(customers);
    }
    @Test void missingAndWrongCredentialsAreIndistinguishable() {
        stored("CUSTOMER", "synthetic-password");
        var service = service();
        assertThatThrownBy(() -> service.authenticate("alice@example.test", "wrong"))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Authentication failed");
        assertThatThrownBy(() -> service.authenticate("missing@example.test", "wrong"))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Authentication failed");
        verifyNoInteractions(customers);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "bad\0", "bad\uD800"})
    void rejectsUnrepresentablePasswordsBeforeCredentialQuery(String password) {
        assertThatThrownBy(() -> service().authenticate("alice@example.test", password)).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(credentials, customers);
    }
    @ParameterizedTest @ValueSource(strings = {"ascii", "unicode", "supplementary"})
    void preservesExact72BytePasswordAndRejectsExtraByte(String kind) {
        String password = switch (kind) { case "unicode" -> "é".repeat(36); case "supplementary" -> "😀".repeat(18); default -> "a".repeat(72); };
        stored("CUSTOMER", password);
        var service = service();
        assertThat(service.authenticate("alice@example.test", password).userId()).isEqualTo(user);
        clearInvocations(credentials);
        assertThatThrownBy(() -> service.authenticate("alice@example.test", password + "x")).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(credentials);
    }
    @Test void profileMissingOrMismatchedNeverAuthenticates() {
        stored("CUSTOMER", "synthetic-password");
        when(customers.findByUserId(user)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().authenticate("alice@example.test", "synthetic-password")).isInstanceOf(BadCredentialsException.class);
        when(customers.findByUserId(user)).thenReturn(Optional.of(new CustomerQueries.Profile(customer, UUID.randomUUID(), "Wrong")));
        assertThatThrownBy(() -> service().authenticate("alice@example.test", "synthetic-password")).isInstanceOf(BadCredentialsException.class);
    }
    @Test void technicalFailuresDoNotBecomeBadCredentialsOrLeakMessages() {
        when(credentials.find(anyString())).thenThrow(new IllegalStateException("private SQL and credential details"));
        assertThatThrownBy(() -> service().authenticate("alice@example.test", "synthetic-password"))
                .isInstanceOf(AuthenticationServiceException.class).hasMessage("Authentication is temporarily unavailable").hasNoCause();
    }
    @Test void upgradesOnlyMatchingOldHashAfterAuthentication() {
        String old = encoder.encode("synthetic-password");
        when(credentials.find(anyString())).thenReturn(Optional.of(new LoginCredentials.Credential(user, "ADMIN", old)));
        when(credentials.upgradeHash(eq(user), eq(old), anyString())).thenReturn(true);
        var service = new AuthenticateUser(credentials, customers, new BCryptPasswordEncoder(5));
        service.authenticate("alice@example.test", "synthetic-password");
        verify(credentials).upgradeHash(eq(user), eq(old), argThat(hash -> hash.startsWith("$2a$05$") && encoder.matches("synthetic-password", hash)));
        when(credentials.upgradeHash(eq(user), eq(old), anyString())).thenReturn(false);
        assertThatThrownBy(() -> service.authenticate("alice@example.test", "synthetic-password")).isInstanceOf(BadCredentialsException.class);
    }
    @Test void currentIdentityRequiresAuthenticatedServerPrincipalAndCorrectRole() {
        var current = new SessionCurrentIdentity();
        assertThatThrownBy(current::requireCustomer).isInstanceOf(InsufficientAuthenticationException.class);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("forged UUID", null, List.of()));
        assertThatThrownBy(current::requireCustomer).isInstanceOf(InsufficientAuthenticationException.class);
        var principal = new SessionPrincipal(user, "CUSTOMER", customer, "Alice");
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        assertThat(current.requireCustomer().customerId()).isEqualTo(customer);
        assertThatThrownBy(current::requireAdmin).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal(user, "ADMIN", null, null), null, List.of()));
        assertThat(current.requireAdmin().userId()).isEqualTo(user);
        assertThatThrownBy(current::requireCustomer).isInstanceOf(AccessDeniedException.class);
    }
    @Test void transportRequiresHttpsAndRejectsCrossOriginMutations() throws Exception {
        var filter = new AuthTransportFilter(true);
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        var called = new AtomicBoolean();
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(called).isFalse();
        request.setSecure(true); request.setScheme("https"); request.setServerPort(443);
        request.addHeader("Origin", "https://other.example.test");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertThat(response.getStatus()).isEqualTo(403);
        request.removeHeader("Origin");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> called.set(true));
        assertThat(called).isTrue();
    }
}
