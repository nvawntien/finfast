package com.finfast.administration.internal;

import com.finfast.common.error.ErrorCode;

public enum AdminErrorCode implements ErrorCode {
    FORBIDDEN_ADMIN_ONLY("FORBIDDEN", "Administrator privileges required to access this resource.", Category.FORBIDDEN),
    UNAUTHORIZED("UNAUTHORIZED", "Authentication required to access administrative APIs.", Category.FORBIDDEN),
    INVALID_QUERY_PARAM("INVALID_QUERY", "Invalid filter, pagination, or sorting parameter.", Category.INVALID_REQUEST),
    TREASURY_NOT_FOUND("TREASURY_NOT_FOUND", "Treasury account has not been initialized.", Category.NOT_FOUND),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "Administration service is unavailable.", Category.UNPROCESSABLE);

    private final String code;
    private final String publicMessage;
    private final Category category;

    AdminErrorCode(String code, String publicMessage, Category category) {
        this.code = code;
        this.publicMessage = publicMessage;
        this.category = category;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String publicMessage() {
        return publicMessage;
    }

    @Override
    public Category category() {
        return category;
    }
}

