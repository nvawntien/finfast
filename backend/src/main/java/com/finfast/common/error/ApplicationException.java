package com.finfast.common.error;

import java.util.Objects;

/** An expected failure; it makes no assertion about a durable financial outcome. */
public final class ApplicationException extends RuntimeException {

    private final String code;
    private final ErrorCode.Category category;

    public ApplicationException(ErrorCode error) {
        this(error, null);
    }

    public ApplicationException(ErrorCode error, Throwable cause) {
        super(requireText(Objects.requireNonNull(error, "error").publicMessage()), cause);
        this.code = requireText(error.code());
        this.category = Objects.requireNonNull(error.category(), "category");
    }

    public String code() {
        return code;
    }

    public String publicMessage() {
        return getMessage();
    }

    public ErrorCode.Category category() {
        return category;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Error codes and public messages must not be blank");
        }
        return value;
    }
}
