package com.finfast.administration.internal.application.usecase;

import com.finfast.common.error.ErrorCode;

/** Safe Administration-local business reasons; no HTTP mapping or technical-failure variant.
 * Amount/reason semantics reuse the approved ordinary-posting reasons without importing Ledger internals.
 */
enum AllocationError implements ErrorCode {
    INVALID_AMOUNT("Amount must be positive"),
    REASON_REQUIRED("Allocation reason is required"),
    RECIPIENT_NOT_FOUND("Recipient Customer Account was not found");

    private final String message;

    AllocationError(String message) {
        this.message = message;
    }

    @Override public String code() { return name(); }
    @Override public String publicMessage() { return message; }
    @Override public Category category() { return Category.UNPROCESSABLE; }
}
