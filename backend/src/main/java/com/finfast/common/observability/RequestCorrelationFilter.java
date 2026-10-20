package com.finfast.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** A request identifier is server-owned and independent of the tracing context. */
public final class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".id";
    private static final String MDC_KEY = "request.id";

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = (String) request.getAttribute(ATTRIBUTE);
        if (id == null) {
            id = UUID.randomUUID().toString();
            request.setAttribute(ATTRIBUTE, id);
        }
        if (!response.isCommitted()) {
            response.setHeader("X-Request-ID", id);
        }
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, id);
        try {
            chain.doFilter(request, response);
        } finally {
            // Restore an enclosing dispatch's scope; otherwise leave the pooled thread clean.
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }

    @Override
    protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        doFilterInternal(request, response, chain);
    }
}
