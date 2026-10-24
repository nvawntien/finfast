package com.finfast.administration.internal.application.dto;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.common.money.VndAmount;
import com.finfast.idempotency.api.RequestOutcomes;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Typed application input only; no caller-supplied actor, source, canonical JSON or financial reference.
 * Zero/blank values remain representable for authorized, replayable business rejection.
 * Raw HTTP tokens, absent required properties and malformed transport input are outside this boundary.
 */
public record AllocationCommand(RequestOutcomes.Key key, AccountNumber recipientNumber,
                                VndAmount amount, String reason) {
    public AllocationCommand {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(recipientNumber, "recipientNumber");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        requirePersistable(key.value());
        requirePersistable(recipientNumber.value());
        requirePersistable(reason);
        if (reason.codePointCount(0, reason.length()) > 500) {
            throw new IllegalArgumentException("Allocation reason must fit 500 characters");
        }
    }

    private static void requirePersistable(String text) {
        if (text.indexOf('\0') >= 0 || !StandardCharsets.UTF_8.newEncoder().canEncode(text)) {
            throw new IllegalArgumentException("Allocation input must be NUL-free valid Unicode");
        }
    }
}
