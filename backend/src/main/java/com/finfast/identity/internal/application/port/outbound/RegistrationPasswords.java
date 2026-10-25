package com.finfast.identity.internal.application.port.outbound;

/** One-way credential storage; no plaintext persistence or diagnostics. */
public interface RegistrationPasswords {
    String hash(String password);
}
