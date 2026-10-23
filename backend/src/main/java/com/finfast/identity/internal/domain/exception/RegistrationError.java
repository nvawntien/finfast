package com.finfast.identity.internal.domain.exception;

import com.finfast.common.error.ErrorCode;

public enum RegistrationError implements ErrorCode {
    INVALID_REGISTRATION(Category.UNPROCESSABLE, "Registration fields are invalid."),
    EMAIL_ALREADY_REGISTERED(Category.CONFLICT, "The email is already registered.");

    private final Category category;
    private final String message;

    RegistrationError(Category category, String message) {
        this.category = category;
        this.message = message;
    }

    @Override public String code() { return name(); }
    @Override public Category category() { return category; }
    @Override public String publicMessage() { return message; }
}
