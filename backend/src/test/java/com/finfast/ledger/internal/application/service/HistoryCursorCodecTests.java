package com.finfast.ledger.internal.application.service;

import com.finfast.ledger.internal.application.dto.HistoryCursor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryCursorCodecTests {
    private static final byte[] KEY = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
    private static final HistoryCursor STATE = new HistoryCursor(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"),
            UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210"), 1000, 900, 20);
    // Independently calculated with Python struct.pack('>B16s16sqqi') and hmac.digest(..., 'sha256').
    private static final String GOLDEN = "AQEjRWeJq83vASNFZ4mrze_-3LqYdlQyEP7cuph2VDIQAAAAAAAAA-gAAAAAAAADhAAAABQ.yWX6GawrFVXFh_ZB7vdEnu4MzWuPS0qVoKOow8ZOqOk";
    private final HistoryCursorCodec codec = new HistoryCursorCodec(KEY);

    @Test
    void canonicalGoldenVectorAndRoundTripRemainStableAcrossRecreation() {
        assertThat(codec.encode(STATE)).isEqualTo(GOLDEN).hasSize(115).doesNotContain("=");
        assertThat(codec.decode(GOLDEN)).isEqualTo(STATE);
        assertThat(new HistoryCursorCodec(KEY.clone()).decode(GOLDEN)).isEqualTo(STATE);
    }

    @Test
    void keyIsDefensivelyCopiedAndDifferentKeyCannotVerifyExistingCursors() {
        byte[] supplied = KEY.clone();
        var original = new HistoryCursorCodec(supplied);
        Arrays.fill(supplied, (byte) 42);
        assertThat(original.encode(STATE)).isEqualTo(GOLDEN);
        assertThatThrownBy(() -> new HistoryCursorCodec(supplied).decode(GOLDEN))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid history cursor");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 100})
    void exactLongMaximumAndPageLimitsRoundTrip(int limit) {
        var state = new HistoryCursor(STATE.ownerCustomerId(), STATE.participantAccountId(), Long.MAX_VALUE, Long.MAX_VALUE, limit);
        assertThat(codec.decode(codec.encode(state))).isEqualTo(state);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 31})
    void inadequateKeyMaterialIsRejected(int bytes) {
        assertThatThrownBy(() -> new HistoryCursorCodec(new byte[bytes])).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("malformedTokens")
    void malformedOrUnboundedInputIsRejected(String token) {
        assertThatThrownBy(() -> codec.decode(token)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid history cursor");
    }

    static Stream<Arguments> malformedTokens() {
        return Stream.of(Arguments.of((String) null), Arguments.of(""), Arguments.of("x".repeat(100_000)),
                Arguments.of(GOLDEN.substring(1)), Arguments.of(GOLDEN.substring(0, 114)), Arguments.of(GOLDEN + "="),
                Arguments.of("!" + GOLDEN.substring(1)), Arguments.of(GOLDEN.replace('.', '/')),
                Arguments.of(GOLDEN.substring(0, 72) + "!" + GOLDEN.substring(73)),
                Arguments.of(GOLDEN.substring(0, 114) + "l"), // Same decoded MAC through unused-bit alias.
                Arguments.of(GOLDEN.substring(0, 70) + "R" + GOLDEN.substring(71)),
                Arguments.of(" " + GOLDEN.substring(1)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 17, 33, 41, 49, 52})
    void modifyingAnyPayloadFieldWithoutItsMacIsRejected(int byteIndex) {
        byte[] payload = Base64.getUrlDecoder().decode(GOLDEN.substring(0, 71));
        payload[byteIndex] ^= 1;
        String altered = Base64.getUrlEncoder().withoutPadding().encodeToString(payload) + GOLDEN.substring(71);
        assertThatThrownBy(() -> codec.decode(altered)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidAuthenticatedFields")
    void evenAuthenticatedUnknownVersionsAndInvalidBoundsAreRejected(int offset, long value, int width) throws Exception {
        byte[] payload = Base64.getUrlDecoder().decode(GOLDEN.substring(0, 71));
        ByteBuffer fields = ByteBuffer.wrap(payload);
        if (width == 1) {
            fields.put(offset, (byte) value);
        } else if (width == 4) {
            fields.putInt(offset, (int) value);
        } else {
            fields.putLong(offset, value);
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String signed = encoder.encodeToString(payload) + "." + encoder.encodeToString(mac.doFinal(payload));
        assertThatThrownBy(() -> codec.decode(signed)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid history cursor");
    }

    static Stream<Arguments> invalidAuthenticatedFields() {
        return Stream.of(Arguments.of(0, 0L, 1), Arguments.of(0, 2L, 1), Arguments.of(33, -1L, 8),
                Arguments.of(33, 0L, 8), Arguments.of(33, 1L, 8), Arguments.of(33, 899L, 8),
                Arguments.of(41, -1L, 8), Arguments.of(41, 0L, 8), Arguments.of(41, 1L, 8), Arguments.of(41, 1001L, 8),
                Arguments.of(49, 0L, 4), Arguments.of(49, 101L, 4), Arguments.of(49, Integer.MAX_VALUE, 4));
    }

    @Test
    void modifyingOnlyTheMacIsRejected() {
        String changed = GOLDEN.substring(0, 72) + (GOLDEN.charAt(72) == 'A' ? 'B' : 'A') + GOLDEN.substring(73);
        assertThatThrownBy(() -> codec.decode(changed)).isInstanceOf(IllegalArgumentException.class);
    }
}
