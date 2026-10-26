package com.finfast.identity.internal.adapter.inbound.http;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/** Preserve auth denials from CurrentIdentity instead of the generic unexpected-error handler. */
@RestControllerAdvice
@Profile("auth")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionAdvice {
    @ExceptionHandler(AuthenticationException.class)
    public void unauthenticated(HttpServletResponse response) throws IOException { AuthJson.error(response, 401); }
    @ExceptionHandler(AccessDeniedException.class)
    public void forbidden(HttpServletResponse response) throws IOException { AuthJson.error(response, 403); }
}
