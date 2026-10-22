package com.finfast.administration.internal;

import com.finfast.common.error.ApplicationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminRbacInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. Check HTTP Header (e.g. for API clients, curl, development, testing)
        String roleHeader = request.getHeader("X-User-Role");
        if (roleHeader != null && "ADMIN".equalsIgnoreCase(roleHeader.trim())) {
            return true;
        }

        // 2. Check HTTP Session attribute
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object roleAttr = session.getAttribute("role");
            if (roleAttr != null && "ADMIN".equalsIgnoreCase(roleAttr.toString().trim())) {
                return true;
            }
        }

        // 3. Fallback: reject unauthorized/non-admin access
        throw new ApplicationException(AdminErrorCode.FORBIDDEN_ADMIN_ONLY);
    }
}

