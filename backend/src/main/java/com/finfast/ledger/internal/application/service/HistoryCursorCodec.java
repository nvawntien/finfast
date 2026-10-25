package com.finfast.ledger.internal.application.service;

import com.finfast.ledger.internal.application.dto.HistoryCursor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * TD-004 v1: 53 network-order bytes (version:u8, owner:uuid, account:uuid, H:i64, last:i64, limit:i32),
 * unpadded base64url(payload) + '.' + unpadded base64url(HMAC-SHA256(payload)). Exactly 115 ASCII characters.
 * Inject at least 256 bits of server-managed key material; no key generation, source, fallback or logging here.
 */
public final class HistoryCursorCodec {
    private static final int PAYLOAD_BYTES = 53;
    private static final int PAYLOAD_CHARACTERS = 71;
    private static final int TOKEN_CHARACTERS = 115;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final SecretKeySpec key;

    public HistoryCursorCodec(byte[] keyMaterial) {
        if (Objects.requireNonNull(keyMaterial, "keyMaterial").length < 32) {
            throw new IllegalArgumentException("History cursor key must contain at least 256 bits");
        }
        this.key = new SecretKeySpec(keyMaterial, "HmacSHA256"); // SecretKeySpec copies the supplied bytes.
    }

    public String encode(HistoryCursor cursor) {
        Objects.requireNonNull(cursor, "cursor");
        byte[] payload = ByteBuffer.allocate(PAYLOAD_BYTES).put((byte) 1)
                .putLong(cursor.ownerCustomerId().getMostSignificantBits()).putLong(cursor.ownerCustomerId().getLeastSignificantBits())
                .putLong(cursor.participantAccountId().getMostSignificantBits()).putLong(cursor.participantAccountId().getLeastSignificantBits())
                .putLong(cursor.highWater()).putLong(cursor.lastSeen()).putInt(cursor.limit()).array();
        return ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(sign(payload));
    }

    public HistoryCursor decode(String token) {
        // Bound work before decoding, splitting or interpreting attacker-controlled fields.
        if (token == null || token.length() != TOKEN_CHARACTERS || token.charAt(PAYLOAD_CHARACTERS) != '.') {
            throw invalid();
        }
        byte[] payload;
        byte[] suppliedTag;
        try {
            payload = DECODER.decode(token.substring(0, PAYLOAD_CHARACTERS));
            suppliedTag = DECODER.decode(token.substring(PAYLOAD_CHARACTERS + 1));
        } catch (IllegalArgumentException malformedEncoding) {
            throw invalid();
        }
        if (payload.length != PAYLOAD_BYTES || suppliedTag.length != 32
                || !MessageDigest.isEqual(sign(payload), suppliedTag)) {
            throw invalid();
        }
        // Reject padding and unused-bit aliases so each authenticated state has one canonical representation.
        if (!ENCODER.encodeToString(payload).equals(token.substring(0, PAYLOAD_CHARACTERS))
                || !ENCODER.encodeToString(suppliedTag).equals(token.substring(PAYLOAD_CHARACTERS + 1))) {
            throw invalid();
        }
        ByteBuffer fields = ByteBuffer.wrap(payload);
        if (fields.get() != 1) {
            throw invalid();
        }
        return new HistoryCursor(new UUID(fields.getLong(), fields.getLong()), new UUID(fields.getLong(), fields.getLong()),
                fields.getLong(), fields.getLong(), fields.getInt());
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(payload);
        } catch (GeneralSecurityException unavailableAlgorithm) {
            throw new IllegalStateException("History cursor authentication unavailable", unavailableAlgorithm);
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid history cursor");
    }
}
