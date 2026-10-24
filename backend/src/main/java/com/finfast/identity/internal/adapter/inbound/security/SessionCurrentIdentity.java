package com.finfast.identity.internal.adapter.inbound.security;

import com.finfast.identity.api.CurrentIdentity;
import com.finfast.identity.internal.application.dto.SessionPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SessionCurrentIdentity implements CurrentIdentity {
    public static SessionPrincipal principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new InsufficientAuthenticationException("Authentication required");
        }
        return principal;
    }

    @Override public CustomerActor requireCustomer() {
        var principal = principal();
        if (!"CUSTOMER".equals(principal.role())) { throw new AccessDeniedException("Customer role required"); }
        return new CustomerActor(principal.userId(), principal.customerId());
    }
    @Override public AdminActor requireAdmin() {
        var principal = principal();
        if (!"ADMIN".equals(principal.role())) { throw new AccessDeniedException("Admin role required"); }
        return new AdminActor(principal.userId());
    }
}
