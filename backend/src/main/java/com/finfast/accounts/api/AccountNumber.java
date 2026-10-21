package com.finfast.accounts.api;

import java.util.Objects;

/** Opaque immutable Account Number, distinct from User/Customer/Account UUIDs.
 * No format/generator is selected; preserve exact text, never trim or reassign.
 * Invalid client text must remain in the canonical intent before mapping here.
 */
public record AccountNumber(String value) {
    public AccountNumber {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.codePointCount(0, value.length()) > 32) {
            throw new IllegalArgumentException("Account Number must be nonblank and fit the existing schema");
        }
    }
}
