package com.finfast.identity.internal.adapter.inbound.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Framework authentication filter retains session strategy/context-save/credential-erasure semantics. */
public final class JsonLoginFilter extends AbstractAuthenticationProcessingFilter {
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public JsonLoginFilter(AuthenticationManager manager) {
        super(request -> "POST".equals(request.getMethod()) && "/api/v1/auth/login".equals(request.getServletPath()), manager);
    }
    @Override public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        String email;
        String password;
        try {
            if (request.getContentType() == null || !request.getContentType().split(";", 2)[0].equalsIgnoreCase("application/json")) {
                throw new IllegalArgumentException();
            }
            byte[] bytes = request.getInputStream().readNBytes(4097);
            if (bytes.length > 4096) { throw new IllegalArgumentException(); }
            var body = json.readTree(bytes);
            if (body == null || !body.isObject() || body.size() != 2 || body.get("email") == null
                    || !body.get("email").isString() || body.get("password") == null || !body.get("password").isString()) {
                throw new IllegalArgumentException();
            }
            email = body.get("email").asString();
            password = body.get("password").asString();
        } catch (Exception error) { throw new BadCredentialsException("Authentication failed"); }
        return getAuthenticationManager().authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
    }
}
