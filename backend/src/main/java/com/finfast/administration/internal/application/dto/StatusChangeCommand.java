package com.finfast.administration.internal.application.dto;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.CustomerAccount;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Typed BK-017 input; authentication and current status are server-owned. */
public record StatusChangeCommand(AccountNumber accountNumber, CustomerAccount.Status desiredStatus, String reason) {
    public StatusChangeCommand {
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(desiredStatus, "desiredStatus");
        Objects.requireNonNull(reason, "reason");
        if (reason.indexOf('\0') >= 0 || !StandardCharsets.UTF_8.newEncoder().canEncode(reason)) {
            throw new IllegalArgumentException("Status reason must be NUL-free valid Unicode");
        }
        if (reason.codePointCount(0, reason.length()) > 500) {
            throw new IllegalArgumentException("Status reason must fit 500 characters");
        }
    }
}
