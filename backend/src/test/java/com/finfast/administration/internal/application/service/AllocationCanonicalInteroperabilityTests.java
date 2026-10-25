package com.finfast.administration.internal.application.service;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.administration.internal.application.dto.AllocationCommand;
import com.finfast.common.money.VndAmount;
import com.finfast.idempotency.api.RequestOutcomes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Administration-local literal vectors against TD-003's typed boundary, NOT Minh's missing shared vectors.
 * No fingerprint, lock encoding, raw malformed HTTP token or version-upgrade claim.
 */
class AllocationCanonicalInteroperabilityTests {
    private static final UUID ADMIN = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final String PREFIX = "{\"initiatorUserId\":\"01234567-89ab-cdef-0123-456789abcdef\","
            + "\"operation\":\"DEMO_ALLOCATION\",\"source\":\"TREASURY\",\"destinationAccountNumber\":\"FF-000001\","
            + "\"currency\":\"VND\",\"amount\":\"9223372036854775807\",\"note\":{\"tag\":\"absent\"},"
            + "\"reason\":{\"tag\":\"present\",\"value\":";

    @ParameterizedTest
    @MethodSource("literalReasons")
    void orderedV1DocumentMatchesIndependentLiteralEscapingAndExactUnicode(String reason, String escaped) {
        var command = command("FF-000001", reason);
        var actual = AllocationCanonicalIntent.create(ADMIN, command);
        assertThat(actual.version()).isEqualTo((short) 1);
        assertThat(actual.json()).isEqualTo(PREFIX + '"' + escaped + "\"}}");
    }

    static Stream<Arguments> literalReasons() {
        return Stream.of(
                Arguments.of(" é e\u0301 😀 ", " é e\u0301 😀 "),
                Arguments.of("\u0001\u000b\u001e\u001f\"\\/\t\n\r\b\f", "\\u0001\\u000B\\u001E\\u001F\\\"\\\\/\\t\\n\\r\\b\\f"),
                Arguments.of("line\u2028paragraph\u2029end", "line\u2028paragraph\u2029end"),
                Arguments.of(" \t  exact trailing  \n", " \\t  exact trailing  \\n"));
    }

    @Test
    void utf8BytesPinCombiningPrecomposedAndSupplementaryCharactersWithoutUsingASerializerAsOracle() {
        var expected = new ByteArrayOutputStream();
        expected.writeBytes((PREFIX + '"').getBytes(StandardCharsets.US_ASCII));
        expected.writeBytes(HexFormat.of().parseHex("20c3a92065cc8120f09f988020"));
        expected.writeBytes("\"}}".getBytes(StandardCharsets.US_ASCII));
        var input = command("FF-000001", " é e\u0301 😀 ");
        for (int repeat = 0; repeat < 4; repeat++) {
            assertThat(AllocationCanonicalIntent.create(ADMIN, input).json().getBytes(StandardCharsets.UTF_8))
                    .isEqualTo(expected.toByteArray());
        }
        assertThat(AllocationCanonicalIntent.create(ADMIN, command("FF-000001", "é")))
                .isNotEqualTo(AllocationCanonicalIntent.create(ADMIN, command("FF-000001", "e\u0301")));
    }

    @Test
    void destinationIsEscapedExactlyWithoutResolvingMutableMetadataOrChangingScopeKey() {
        var input = command(" Q\"\\\t😀 ", "reason");
        String expected = "{\"initiatorUserId\":\"01234567-89ab-cdef-0123-456789abcdef\","
                + "\"operation\":\"DEMO_ALLOCATION\",\"source\":\"TREASURY\",\"destinationAccountNumber\":\" Q\\\"\\\\\\t😀 \","
                + "\"currency\":\"VND\",\"amount\":\"9223372036854775807\",\"note\":{\"tag\":\"absent\"},"
                + "\"reason\":{\"tag\":\"present\",\"value\":\"reason\"}}";
        assertThat(AllocationCanonicalIntent.create(ADMIN, input).json()).isEqualTo(expected);
        assertThat(input.key().value()).isEqualTo("  Key 😀 e\u0301  ");
    }

    private static AllocationCommand command(String number, String reason) {
        return new AllocationCommand(new RequestOutcomes.Key("  Key 😀 e\u0301  "), new AccountNumber(number),
                new VndAmount(Long.MAX_VALUE), reason);
    }
}
