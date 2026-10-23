package com.finfast.identity.internal.application.usecase;

import com.finfast.common.error.ApplicationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Internal input only. Neither credentials nor personal fields belong in diagnostic output. */
public record RegistrationCommand(@NotBlank String displayName, @NotBlank @Email String email,
                                  @NotBlank String password) {
    public RegistrationCommand validated(Validator validator) {
        if (!validator.validate(this).isEmpty()
                || !persistable(displayName, 120) || !persistable(email, 254)
                || !persistable(password, 72) || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApplicationException(RegistrationError.INVALID_REGISTRATION);
        }
        // Case normalization only: do not silently trim/normalize passwords or display names.
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        if (!persistable(normalizedEmail, 254)) {
            throw new ApplicationException(RegistrationError.INVALID_REGISTRATION);
        }
        return new RegistrationCommand(displayName, normalizedEmail, password);
    }

    private static boolean persistable(String value, int maxCodePoints) {
        return value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= maxCodePoints
                && value.codePoints().noneMatch(cp -> cp == 0 || cp >= 0xD800 && cp <= 0xDFFF);
    }

    @Override public String toString() { return "RegistrationCommand[REDACTED]"; }
}
