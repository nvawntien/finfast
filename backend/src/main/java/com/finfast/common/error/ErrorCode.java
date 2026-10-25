package com.finfast.common.error;

/** Implement with an immutable, module-owned catalog (normally an enum).
 * Public messages must be safe constants, never exception messages or user input.
 */
public interface ErrorCode {

    String code();

    String publicMessage();

    Category category();

    enum Category {
        INVALID_REQUEST, NOT_FOUND, CONFLICT, FORBIDDEN, UNPROCESSABLE, UNAUTHORIZED
    }
}
