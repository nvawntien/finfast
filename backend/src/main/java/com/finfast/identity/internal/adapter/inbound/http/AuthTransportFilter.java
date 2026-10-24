package com.finfast.identity.internal.adapter.inbound.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/** HTTPS by default; reject cross-origin mutations in the selected same-origin topology. */
public final class AuthTransportFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final boolean requireHttps;
    public AuthTransportFilter(boolean requireHttps) { this.requireHttps = requireHttps; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean foreignOrigin;
        try { foreignOrigin = !SAFE.contains(request.getMethod()) && CorsUtils.isCorsRequest(request); }
        catch (IllegalArgumentException error) { foreignOrigin = true; }
        if ((request.getServletPath().startsWith("/api/") && requireHttps && !request.isSecure()) || foreignOrigin) {
            AuthJson.error(response, 403);
            return;
        }
        chain.doFilter(request, response);
    }
}
