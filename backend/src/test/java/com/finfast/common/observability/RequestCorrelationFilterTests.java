package com.finfast.common.observability;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestCorrelationFilterTests {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @BeforeEach
    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void serverGeneratedIdIgnoresIncomingValueAndIsRemovedAfterCompletion() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", HttpTracingFixtures.CANARY);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            String id = MDC.get("request.id");
            assertThat(UUID.fromString(id).version()).isEqualTo(4);
            assertThat(id).isEqualTo(response.getHeader("X-Request-ID")).isNotEqualTo(HttpTracingFixtures.CANARY);
            assertThat(MDC.get("traceId")).isNull();
        });
        assertThat(MDC.get("request.id")).isNull();
    }

    @Test
    void failureStillCleansUpContext() {
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> { throw new IOException("test-only failure"); })).isInstanceOf(IOException.class);
        assertThat(MDC.get("request.id")).isNull();
    }

    @Test
    void sequentialRequestsOnOneThreadGetDifferentIds() throws Exception {
        var first = new MockHttpServletResponse();
        var second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), first, (req, res) -> {});
        filter.doFilter(new MockHttpServletRequest(), second, (req, res) -> {});
        assertThat(first.getHeader("X-Request-ID")).isNotEqualTo(second.getHeader("X-Request-ID"));
        assertThat(MDC.get("request.id")).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = DispatcherType.class, names = {"ERROR", "ASYNC"})
    void servletRedispatchReusesServerIdAndCleansUpEachThreadScope(DispatcherType type) throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        String id = response.getHeader("X-Request-ID");
        request.setDispatcherType(type);
        if (type == DispatcherType.ERROR) {
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/test-only/error");
        }
        var redispatchResponse = new MockHttpServletResponse();
        filter.doFilter(request, redispatchResponse, (req, res) -> assertThat(MDC.get("request.id")).isEqualTo(id));
        assertThat(redispatchResponse.getHeader("X-Request-ID")).isEqualTo(id);
        assertThat(MDC.get("request.id")).isNull();
    }

    @Test
    void nestedErrorDispatchPreservesTheEnclosingRequestScope() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            String id = MDC.get("request.id");
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/test-only/error");
            filter.doFilter(request, response, (innerReq, innerRes) -> assertThat(MDC.get("request.id")).isEqualTo(id));
            assertThat(MDC.get("request.id")).isEqualTo(id);
        });
        assertThat(MDC.get("request.id")).isNull();
    }

    @Test
    void existingOuterScopeIsRestoredWithoutReusingItsId() throws Exception {
        MDC.put("request.id", "outer-scope");
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> assertThat(MDC.get("request.id")).isNotEqualTo("outer-scope"));
        assertThat(MDC.get("request.id")).isEqualTo("outer-scope");
    }
}
