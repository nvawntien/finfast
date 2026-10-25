package com.finfast.identity.internal;

import com.finfast.common.error.ApplicationException;
import com.finfast.customer.api.CustomerProvisioning;
import com.finfast.identity.internal.adapter.outbound.security.BcryptRegistrationPasswords;
import com.finfast.identity.internal.application.port.outbound.RegistrationPasswords;
import com.finfast.identity.internal.application.port.outbound.RegistrationUsers;
import com.finfast.identity.internal.application.usecase.RegisterCustomer;
import com.finfast.identity.internal.application.dto.RegistrationCommand;
import com.finfast.identity.internal.application.port.inbound.CustomerRegistration;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RegistrationValidationTests {
    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();

    @AfterAll static void closeValidator() { VALIDATION.close(); }

    static Stream<RegistrationCommand> invalidCommands() {
        return Stream.of(
                null,
                new RegistrationCommand(null, "alice@example.test", "synthetic-password"),
                new RegistrationCommand(" ", "alice@example.test", "synthetic-password"),
                new RegistrationCommand("😀".repeat(121), "alice@example.test", "synthetic-password"),
                new RegistrationCommand("Alice\0", "alice@example.test", "synthetic-password"),
                new RegistrationCommand("Alice\uD800", "alice@example.test", "synthetic-password"),
                new RegistrationCommand("Alice", null, "synthetic-password"),
                new RegistrationCommand("Alice", "not-email", "synthetic-password"),
                new RegistrationCommand("Alice", " alice@example.test", "synthetic-password"),
                new RegistrationCommand("Alice", "alice@example.test ", "synthetic-password"),
                new RegistrationCommand("Alice", "alice\0@example.test", "synthetic-password"),
                new RegistrationCommand("Alice", "a".repeat(255) + "@example.test", "synthetic-password"),
                new RegistrationCommand("Alice", "alice@example.test", null),
                new RegistrationCommand("Alice", "alice@example.test", "  "),
                new RegistrationCommand("Alice", "alice@example.test", "a".repeat(73)),
                new RegistrationCommand("Alice", "alice@example.test", "é".repeat(37)),
                new RegistrationCommand("Alice", "alice@example.test", "😀".repeat(19)),
                new RegistrationCommand("Alice", "alice@example.test", "secret\0"),
                new RegistrationCommand("Alice", "alice@example.test", "secret\uD800"));
    }

    @ParameterizedTest
    @MethodSource("invalidCommands")
    void invalidInputNeverHashesOrTouchesPersistence(RegistrationCommand input) {
        var users = mock(RegistrationUsers.class);
        var passwords = mock(RegistrationPasswords.class);
        var customers = mock(CustomerProvisioning.class);
        var manager = mock(PlatformTransactionManager.class);
        CustomerRegistration service = new RegisterCustomer(users, passwords, customers, VALIDATION.getValidator(), manager);
        assertThatThrownBy(() -> service.register(input)).isInstanceOf(ApplicationException.class)
                .hasMessage("Registration fields are invalid.");
        verifyNoInteractions(users, passwords, customers, manager);
    }

    @Test
    void preservesTextAndNormalizesEmailIndependentlyOfHostLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var result = new RegistrationCommand("😀".repeat(120), "ALICE@EXAMPLE.TEST", "  synthetic  ")
                    .validated(VALIDATION.getValidator());
            assertThat(result.displayName()).isEqualTo("😀".repeat(120));
            assertThat(result.email()).isEqualTo("alice@example.test");
            assertThat(result.password()).isEqualTo("  synthetic  ");
            assertThat(result.toString()).isEqualTo("RegistrationCommand[REDACTED]");
        } finally { Locale.setDefault(previous); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ascii", "unicode", "supplementary"})
    void bcryptAcceptsExactly72Utf8BytesWithoutNormalization(String kind) {
        String password = switch (kind) {
            case "unicode" -> "é".repeat(36);
            case "supplementary" -> "😀".repeat(18);
            default -> "a".repeat(72);
        };
        var hasher = new BcryptRegistrationPasswords(4); // Low cost only for repeated boundary tests.
        String hash = hasher.hash(password);
        assertThat(new BCryptPasswordEncoder().matches(password, hash)).isTrue();
        assertThatThrownBy(() -> hasher.hash(password + "x")).isInstanceOf(ApplicationException.class);
    }

    @Test
    void defaultBcryptUsesCost12RandomSaltAndNeverStoresPlaintext() {
        var hasher = new BcryptRegistrationPasswords();
        long started = System.nanoTime();
        String first = hasher.hash("synthetic-password");
        String second = hasher.hash("synthetic-password");
        long millis = (System.nanoTime() - started) / 1_000_000;
        assertThat(first).startsWith("$2a$12$").hasSize(60).isNotEqualTo(second);
        assertThat(first).doesNotContain("synthetic-password");
        var verifier = new BCryptPasswordEncoder();
        assertThat(verifier.matches("synthetic-password", first)).isTrue();
        assertThat(verifier.matches("wrong-password", first)).isFalse();
        assertThat(verifier.matches(" synthetic-password", first)).isFalse();
        System.out.println("BK-005 BCrypt cost 12: two encodes took " + millis + " ms (local observation, not an SLO)");
    }
}
