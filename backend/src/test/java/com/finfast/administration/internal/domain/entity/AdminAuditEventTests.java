package com.finfast.administration.internal.domain.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AdminAuditEventTests {

    private static final UUID AUDIT = new UUID(0, 1);
    private static final UUID ADMIN = new UUID(0, 2);
    private static final UUID TARGET = new UUID(0, 3);
    private static final UUID TRANSACTION = new UUID(0, 4);
    private static final String REASON = "  Approved synthetic demo funds  ";
    private static final Instant CREATED_AT = Instant.parse("2026-10-23T01:02:03.123456789Z");

    @Test
    void successfulAllocationPreservesAllSuppliedFactsAndHasFixedActionAndOutcome() {
        AdminAuditEvent event = withReason(REASON);

        assertThat(event.id()).isEqualTo(AUDIT);
        assertThat(event.adminUserId()).isEqualTo(ADMIN);
        assertThat(event.targetAccountId()).isEqualTo(TARGET);
        assertThat(event.transactionId()).isEqualTo(TRANSACTION);
        assertThat(event.reason()).isEqualTo(REASON);
        assertThat(event.createdAt()).isEqualTo(CREATED_AT);
        assertThat(event.action()).isEqualTo(AdminAuditEvent.Action.DEMO_ALLOCATION);
        assertThat(event.outcome()).isEqualTo(AdminAuditEvent.Outcome.SUCCESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "  exact reason  ", "\tPhân bổ vốn thử nghiệm\n", "e\u0301", "é", "Demo 😀"})
    void preservesReasonExactlyWithoutTrimmingOrNormalization(String reason) {
        assertThat(withReason(reason).reason()).isEqualTo(reason);
    }

    @ParameterizedTest(name = "requires {0}")
    @MethodSource("missingRequiredValues")
    void rejectsIncompleteAudit(String field, Runnable construction) {
        assertThatNullPointerException().isThrownBy(construction::run).withMessage(field);
    }

    private static Stream<Arguments> missingRequiredValues() {
        return Stream.of(
                Arguments.of("id", (Runnable) () -> new AdminAuditEvent(null, ADMIN, TARGET, REASON, TRANSACTION, CREATED_AT)),
                Arguments.of("adminUserId", (Runnable) () -> new AdminAuditEvent(AUDIT, null, TARGET, REASON, TRANSACTION, CREATED_AT)),
                Arguments.of("targetAccountId", (Runnable) () -> new AdminAuditEvent(AUDIT, ADMIN, null, REASON, TRANSACTION, CREATED_AT)),
                Arguments.of("reason", (Runnable) () -> withReason(null)),
                Arguments.of("transactionId", (Runnable) () -> new AdminAuditEvent(AUDIT, ADMIN, TARGET, REASON, null, CREATED_AT)),
                Arguments.of("createdAt", (Runnable) () -> new AdminAuditEvent(AUDIT, ADMIN, TARGET, REASON, TRANSACTION, null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\r\n", "\u2003", "\u000b\f"})
    void rejectsEmptyAndBlankReasons(String reason) {
        assertThatIllegalArgumentException().isThrownBy(() -> withReason(reason));
    }

    @ParameterizedTest
    @MethodSource("maximumReasons")
    void acceptsExactlyFiveHundredUnicodeCodePoints(String reason) {
        assertThat(reason.codePointCount(0, reason.length())).isEqualTo(500);
        assertThat(withReason(reason).reason()).isEqualTo(reason);
    }

    private static Stream<String> maximumReasons() {
        return Stream.of("a".repeat(500), "😀".repeat(500), "e\u0301".repeat(250), "a" + " ".repeat(499));
    }

    @ParameterizedTest
    @MethodSource("overlongReasons")
    void rejectsOverlongReasonsIncludingTrailingSpaces(String reason) {
        assertThat(reason.codePointCount(0, reason.length())).isEqualTo(501);
        assertThatIllegalArgumentException().isThrownBy(() -> withReason(reason));
    }

    private static Stream<String> overlongReasons() {
        return Stream.of("a".repeat(501), "😀".repeat(500) + "a", "a" + " ".repeat(500));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\0reason", "rea\0son", "reason\0", "\0"})
    void rejectsNulAnywhereInReason(String reason) {
        assertThatIllegalArgumentException().isThrownBy(() -> withReason(reason));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\uD800abc", "abc\uD800", "\uDC00", "\uDC00\uD800", "😀\uD800",
            "\uD800a\uDC00", "\uD800\uD800", "😀\uDC00"})
    void rejectsMalformedUnicodeRatherThanReplacingIt(String reason) {
        assertThatIllegalArgumentException().isThrownBy(() -> withReason(reason));
    }

    @Test
    void auditHasOnlyFinalImmutableState() {
        assertThat(AdminAuditEvent.class.isRecord()).isTrue();
        assertThat(Modifier.isFinal(AdminAuditEvent.class.getModifiers())).isTrue();
        assertThat(AdminAuditEvent.class.getRecordComponents()).allSatisfy(component ->
                assertThat(component.getType()).isIn(UUID.class, String.class, Instant.class));
        assertThat(AdminAuditEvent.class.getDeclaredFields())
                .filteredOn(field -> !Modifier.isStatic(field.getModifiers()))
                .allSatisfy(field -> assertThat(Modifier.isFinal(field.getModifiers())).isTrue());
    }

    private static AdminAuditEvent withReason(String reason) {
        return new AdminAuditEvent(AUDIT, ADMIN, TARGET, reason, TRANSACTION, CREATED_AT);
    }
}
