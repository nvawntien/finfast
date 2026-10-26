package com.finfast.accounts.internal.domain.exception;

import com.finfast.common.error.ErrorCode;

import java.util.Objects;

/**
 * Immutable, module-owned error code catalog for the accounts module.
 * Public messages are safe constants that do not leak internal/sensitive details.
 */
public enum AccountErrorCode implements ErrorCode {
    ACCOUNT_NOT_FOUND("Account not found", Category.NOT_FOUND),
    CUSTOMER_ACCOUNT_NOT_FOUND("Customer account not found", Category.NOT_FOUND),
    TREASURY_ACCOUNT_NOT_FOUND("Treasury account not found", Category.NOT_FOUND),
    INSUFFICIENT_FUNDS("Insufficient available funds", Category.UNPROCESSABLE),
    INSUFFICIENT_TREASURY_FUNDS("Insufficient treasury funds", Category.UNPROCESSABLE),
    SOURCE_ACCOUNT_NOT_FOUND("Source account not found", Category.NOT_FOUND),
    SOURCE_ACCOUNT_UNAUTHORIZED("Source account does not belong to customer", Category.FORBIDDEN),
    SOURCE_ACCOUNT_FROZEN("Source account is frozen", Category.UNPROCESSABLE),
    RECIPIENT_ACCOUNT_NOT_FOUND("Recipient account not found", Category.NOT_FOUND),
    RECIPIENT_ACCOUNT_FROZEN("Recipient account is frozen", Category.UNPROCESSABLE),
    SELF_TRANSFER_FORBIDDEN("Cannot transfer funds to the same account", Category.UNPROCESSABLE),
    ACCOUNT_NOT_ACTIVE("Account is not active", Category.UNPROCESSABLE),
    ACCOUNT_CURRENCY_MISMATCH("Account currency mismatch", Category.UNPROCESSABLE),
    BALANCE_OVERFLOW("Resulting balance exceeds supported bounds", Category.UNPROCESSABLE),
    INVALID_ACCOUNT_TYPE("Account type is ineligible for this operation", Category.UNPROCESSABLE),
    DUPLICATE_CUSTOMER_ACCOUNT("Customer already has an account", Category.CONFLICT),
    TREASURY_ALREADY_EXISTS("Treasury account already exists", Category.CONFLICT),
    TREASURY_STATUS_IMMUTABLE("Treasury account status cannot be modified", Category.FORBIDDEN),
    TREASURY_ALREADY_INITIALIZED("Treasury account is already initialized", Category.CONFLICT);

    private final String publicMessage;
    private final Category category;

    AccountErrorCode(String publicMessage, Category category) {
        this.publicMessage = Objects.requireNonNull(publicMessage, "publicMessage");
        this.category = Objects.requireNonNull(category, "category");
    }

    @Override
    public String code() {
        return name();
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
