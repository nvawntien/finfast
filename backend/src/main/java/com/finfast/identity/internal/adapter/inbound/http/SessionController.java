package com.finfast.identity.internal.adapter.inbound.http;

import com.finfast.identity.internal.application.dto.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile("auth")
public class SessionController {
    @GetMapping("/api/v1/auth/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("token", token.getToken());
    }
    @GetMapping("/api/v1/auth/session")
    public Map<String, Object> session(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new InsufficientAuthenticationException("Authentication required");
        }
        return AuthJson.session(principal);
    }
}
