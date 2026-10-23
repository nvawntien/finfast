package com.finfast.administration.internal.application.usecase;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.administration.internal.application.dto.AllocationCommand;
import com.finfast.administration.internal.application.service.AllocationCanonicalIntent;
import com.finfast.common.money.VndAmount;
import com.finfast.idempotency.api.RequestOutcomes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllocationCommandAndCanonicalIntentTests {
    private static final UUID ADMIN = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final RequestOutcomes.Key KEY = new RequestOutcomes.Key(" Exact Key 😀 ");
    private static final AccountNumber NUMBER = new AccountNumber("FF-000001");
    private static final String PREFIX = "{\"initiatorUserId\":\"01234567-89ab-cdef-0123-456789abcdef\","
            + "\"operation\":\"DEMO_ALLOCATION\",\"source\":\"TREASURY\",\"destinationAccountNumber\":\"FF-000001\","
            + "\"currency\":\"VND\",\"amount\":\"1000000\",\"note\":{\"tag\":\"absent\"},"
            + "\"reason\":{\"tag\":\"present\",\"value\":";

    @Test
    void literalTypedV1VectorPinsFieldOrderTagsAndSingletonSource() {
        var intent = AllocationCanonicalIntent.create(ADMIN, command("Seed demo"));
        assertThat(intent.version()).isEqualTo((short) 1);
        assertThat(intent.json()).isEqualTo(PREFIX + "\"Seed demo\"}}");
    }

    @Test
    void literalVectorPinsJsonEscapingWithoutChangingText() {
        String reason = "quote\" slash\\ \n\t\b\f\r\u001f";
        var intent = AllocationCanonicalIntent.create(ADMIN, command(reason));
        assertThat(intent.json()).isEqualTo(PREFIX + "\"quote\\\" slash\\\\ \\n\\t\\b\\f\\r\\u001F\"}}");
        assertThat(JsonMapper.builder().build().readTree(intent.json()).get("reason").get("value").asText()).isEqualTo(reason);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  lý do 😀  ", "e\u0301", "é", "\tDemo\n", "", " "})
    void exactUtf8GoldenTextIncludesWhitespaceAndDistinctPresentEmptyReason(String reason) {
        var intent = AllocationCanonicalIntent.create(ADMIN, command(reason));
        String encoded = JsonMapper.builder().build().writeValueAsString(reason);
        assertThat(intent.json().getBytes(StandardCharsets.UTF_8))
                .isEqualTo((PREFIX + encoded + "}}").getBytes(StandardCharsets.UTF_8));
        assertThat(JsonMapper.builder().build().readTree(intent.json()).get("reason").get("value").asText()).isEqualTo(reason);
    }

    @Test
    void unicodeVectorIsLiteralAndNeverNormalized() {
        String expected = PREFIX + "\"  lý do 😀 é  \"}}";
        assertThat(AllocationCanonicalIntent.create(ADMIN, command("  lý do 😀 e\u0301  ")).json()
                .getBytes(StandardCharsets.UTF_8)).isEqualTo(expected.getBytes(StandardCharsets.UTF_8));
        assertThat(AllocationCanonicalIntent.create(ADMIN, command("é")))
                .isNotEqualTo(AllocationCanonicalIntent.create(ADMIN, command("e\u0301")));
    }

    @Test
    void amountIsAnExactDecimalStringIncludingZeroAndLongMaximum() {
        for (long value : new long[]{0, 1, Long.MAX_VALUE}) {
            var command = new AllocationCommand(KEY, NUMBER, new VndAmount(value), "reason");
            var json = JsonMapper.builder().build().readTree(AllocationCanonicalIntent.create(ADMIN, command).json());
            assertThat(json.get("amount").isString()).isTrue();
            assertThat(json.get("amount").asText()).isEqualTo(Long.toString(value));
        }
    }

    @Test
    void allIntentFieldsMatterButKeyIsOnlyPartOfScope() {
        var original = AllocationCanonicalIntent.create(ADMIN, command("reason"));
        assertThat(original).isNotEqualTo(AllocationCanonicalIntent.create(UUID.randomUUID(), command("reason")));
        assertThat(original).isNotEqualTo(AllocationCanonicalIntent.create(ADMIN, command("reason ")));
        assertThat(original).isNotEqualTo(AllocationCanonicalIntent.create(ADMIN,
                new AllocationCommand(KEY, new AccountNumber("FF-000002"), new VndAmount(1000000), "reason")));
        assertThat(original).isNotEqualTo(AllocationCanonicalIntent.create(ADMIN,
                new AllocationCommand(KEY, NUMBER, new VndAmount(1000001), "reason")));
        assertThat(original).isEqualTo(AllocationCanonicalIntent.create(ADMIN,
                new AllocationCommand(new RequestOutcomes.Key("different"), NUMBER, new VndAmount(1000000), "reason")));
    }

    @ParameterizedTest
    @MethodSource("incompleteCommands")
    void incompleteTypedCommandsFailBeforeIntentAcceptance(Runnable construction) {
        assertThatThrownBy(construction::run).isInstanceOf(NullPointerException.class);
    }

    static Stream<Runnable> incompleteCommands() {
        return Stream.of(() -> new AllocationCommand(null, NUMBER, new VndAmount(1), "r"),
                () -> new AllocationCommand(KEY, null, new VndAmount(1), "r"),
                () -> new AllocationCommand(KEY, NUMBER, null, "r"),
                () -> new AllocationCommand(KEY, NUMBER, new VndAmount(1), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x\0x", "x\uD800", "\uDC00x"})
    void everyPersistedInputRejectsNulAndMalformedUnicode(String invalid) {
        assertThatThrownBy(() -> command(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AllocationCommand(new RequestOutcomes.Key(invalid), NUMBER, new VndAmount(1), "r"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AllocationCommand(KEY, new AccountNumber(invalid), new VndAmount(1), "r"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reasonBoundsCountCodePointsWithoutTruncatingTrailingSpaces() {
        assertThat(command("😀".repeat(500)).reason()).isEqualTo("😀".repeat(500));
        assertThatThrownBy(() -> command("😀".repeat(501))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("x" + " ".repeat(500))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VndAmount(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static AllocationCommand command(String reason) {
        return new AllocationCommand(KEY, NUMBER, new VndAmount(1000000), reason);
    }
}
