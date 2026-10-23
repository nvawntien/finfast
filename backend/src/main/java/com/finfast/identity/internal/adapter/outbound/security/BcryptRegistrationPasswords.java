package com.finfast.identity.internal.adapter.outbound.security;

import com.finfast.common.error.ApplicationException;
import com.finfast.identity.internal.application.port.outbound.RegistrationPasswords;
import com.finfast.identity.internal.domain.exception.RegistrationError;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;

/** TD-007 default strength 12; explicit composition may tune cost after measurement. */
public final class BcryptRegistrationPasswords implements RegistrationPasswords {
    private final BCryptPasswordEncoder encoder;

    public BcryptRegistrationPasswords() { this(12); }

    public BcryptRegistrationPasswords(int strength) { encoder = new BCryptPasswordEncoder(strength); }

    @Override
    public String hash(String password) {
        if (password == null || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.codePoints().anyMatch(cp -> cp == 0 || cp >= 0xD800 && cp <= 0xDFFF)) {
            throw new ApplicationException(RegistrationError.INVALID_REGISTRATION);
        }
        return encoder.encode(password);
    }
}
