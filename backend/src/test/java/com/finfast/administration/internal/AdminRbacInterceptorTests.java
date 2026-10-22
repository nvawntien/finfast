package com.finfast.administration.internal;

import com.finfast.common.error.ApplicationException;
import com.finfast.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminRbacInterceptorTests {

    private AdminRbacInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new AdminRbacInterceptor();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void allowsRequestWithAdminRoleHeader() {
        request.addHeader("X-User-Role", "ADMIN");
        boolean allowed = interceptor.preHandle(request, response, new Object());
        assertThat(allowed).isTrue();
    }

    @Test
    void allowsRequestWithAdminRoleInSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("role", "ADMIN");
        request.setSession(session);

        boolean allowed = interceptor.preHandle(request, response, new Object());
        assertThat(allowed).isTrue();
    }

    @Test
    void rejectsAnonymousRequestWithoutCredentials() {
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> {
                    ApplicationException appEx = (ApplicationException) ex;
                    assertThat(appEx.category()).isEqualTo(ErrorCode.Category.FORBIDDEN);
                    assertThat(appEx.code()).isEqualTo("FORBIDDEN");
                });
    }

    @Test
    void rejectsRequestWithCustomerRole() {
        request.addHeader("X-User-Role", "CUSTOMER");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> {
                    ApplicationException appEx = (ApplicationException) ex;
                    assertThat(appEx.category()).isEqualTo(ErrorCode.Category.FORBIDDEN);
                    assertThat(appEx.code()).isEqualTo("FORBIDDEN");
                });
    }

    @Test
    void rejectsRequestWithCustomerRoleInSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("role", "CUSTOMER");
        request.setSession(session);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> {
                    ApplicationException appEx = (ApplicationException) ex;
                    assertThat(appEx.category()).isEqualTo(ErrorCode.Category.FORBIDDEN);
                });
    }
}

