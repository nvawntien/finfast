package com.finfast.ledger.internal.application.usecase;

import com.finfast.common.error.ErrorCode;

/** Tech Lead-approved ordinary posting eligibility errors; final outcome persistence belongs to the caller. */
public enum LedgerPostingError implements ErrorCode {
    INVALID_AMOUNT("Amount must be positive"),
    REASON_REQUIRED("Allocation reason is required");

    private final String message;

    LedgerPostingError(String message) {
        this.message = message;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String publicMessage() {
        return message;
    }

    @Override
    public Category category() {
        return Category.UNPROCESSABLE;
    }
}
