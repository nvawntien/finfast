package com.finfast.identity.internal.application.usecase;

import com.finfast.customer.api.CustomerQueries;
import com.finfast.identity.internal.application.dto.Credential;
import com.finfast.identity.internal.application.dto.SessionPrincipal;
import com.finfast.identity.internal.application.port.inbound.UserAuthentication;
import com.finfast.identity.internal.application.port.outbound.LoginCredentials;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** Verifies Identity credentials before querying Customer through its approved API. */
public final class AuthenticateUser implements UserAuthentication {
    private final LoginCredentials credentials;
    private final CustomerQueries customers;
    private final PasswordEncoder encoder;
    private final String dummyHash;

    public AuthenticateUser(LoginCredentials credentials, CustomerQueries customers, PasswordEncoder encoder) {
        this.credentials = credentials;
        this.customers = customers;
        this.encoder = encoder;
        dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public SessionPrincipal authenticate(String email, String password) {
        if (!valid(email, 254) || !valid(password, 72) || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw denied();
        }
        String normalized = email.toLowerCase(Locale.ROOT);
        if (!valid(normalized, 254)) { throw denied(); }
        try {
            var stored = credentials.find(normalized);
            String hash = stored.map(Credential::passwordHash).orElse(dummyHash);
            boolean matches = encoder.matches(password, hash);
            if (stored.isEmpty() || !matches) { throw denied(); }
            var user = stored.orElseThrow();
            SessionPrincipal principal;
            if ("CUSTOMER".equals(user.role())) {
                var profile = customers.findByUserId(user.userId()).orElseThrow(AuthenticateUser::denied);
                if (!user.userId().equals(profile.userId())) { throw denied(); }
                principal = new SessionPrincipal(user.userId(), user.role(), profile.customerId(), profile.displayName());
            } else if ("ADMIN".equals(user.role())) {
                principal = new SessionPrincipal(user.userId(), user.role(), null, null);
            } else { throw denied(); }
            if (encoder.upgradeEncoding(hash) && !credentials.upgradeHash(user.userId(), hash, encoder.encode(password))) {
                throw denied(); // Never overwrite a concurrently changed credential or authenticate stale evidence.
            }
            return principal;
        } catch (BadCredentialsException error) {
            throw error;
        } catch (RuntimeException error) {
            // Do not carry JDBC/credential messages into the HTTP/security diagnostics boundary.
            throw new AuthenticationServiceException("Authentication is temporarily unavailable");
        }
    }

    private static BadCredentialsException denied() { return new BadCredentialsException("Authentication failed"); }
    private static boolean valid(String value, int max) {
        return value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= max
                && value.codePoints().noneMatch(cp -> cp == 0 || cp >= 0xD800 && cp <= 0xDFFF);
    }
}
