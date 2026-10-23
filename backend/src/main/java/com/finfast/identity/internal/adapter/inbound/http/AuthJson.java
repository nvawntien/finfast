package com.finfast.identity.internal.adapter.inbound.http;

import com.finfast.common.error.ErrorResponse;
import com.finfast.identity.internal.application.dto.SessionPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AuthJson {
    private static final JsonMapper JSON = new JsonMapper();
    private AuthJson() { }
    public static Map<String, Object> session(SessionPrincipal principal) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("userId", principal.userId().toString());
        value.put("role", principal.role());
        if (principal.displayName() != null) { value.put("displayName", principal.displayName()); }
        return value;
    }
    public static void error(HttpServletResponse response, int status) throws IOException {
        String code = status == 503 ? "SERVICE_UNAVAILABLE" : status == 403 ? "FORBIDDEN" : "UNAUTHENTICATED";
        String message = status == 503 ? "Authentication is temporarily unavailable." : status == 403 ? "Access denied." : "Authentication required or failed.";
        write(response, status, new ErrorResponse(code, message));
    }
    public static void write(HttpServletResponse response, int status, Object value) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getOutputStream().write(JSON.writeValueAsBytes(value));
    }
}
