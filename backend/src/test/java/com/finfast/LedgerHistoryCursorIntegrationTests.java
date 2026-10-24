package com.finfast;

import com.finfast.ledger.api.LedgerQueries;
import com.finfast.ledger.api.LedgerQueries.PageRequest;
import com.finfast.ledger.internal.application.dto.HistoryCursor;
import com.finfast.ledger.internal.application.port.inbound.ReadCustomerHistory;
import com.finfast.ledger.internal.application.service.HistoryCursorCodec;
import com.finfast.ledger.internal.application.usecase.CustomerHistoryQueryService;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(45)
class LedgerHistoryCursorIntegrationTests extends LedgerHistoryTestSupport {
    private String continuation() {
        seedDataset(1);
        append(Type.INTERNAL_TRANSFER, 1, "first", TIME, source, destination);
        append(Type.INTERNAL_TRANSFER, 2, "second", TIME, destination, source);
        String cursor = history.history(source, new PageRequest(1, Optional.empty())).nextCursor().orElseThrow();
        observed.resetCounts();
        identities.calls.clear();
        return cursor;
    }

    @ParameterizedTest
    @ValueSource(strings = {"payload", "mac", "truncated", "invalidBase64", "oversized", "wrongKey", "unknownVersion", "invalidBounds"})
    void invalidCursorsCannotReachAccountOrFinancialLookups(String defect) throws Exception {
        String original = continuation();
        byte[] payload = Base64.getUrlDecoder().decode(original.substring(0, 71));
        String token;
        switch (defect) {
            case "payload" -> {
                ByteBuffer.wrap(payload).putLong(33, 999); // Untrusted fields recomputed without a valid MAC.
                token = Base64.getUrlEncoder().withoutPadding().encodeToString(payload) + original.substring(71);
            }
            case "mac" -> token = original.substring(0, 72) + (original.charAt(72) == 'A' ? 'B' : 'A') + original.substring(73);
            case "truncated" -> token = original.substring(0, 100);
            case "invalidBase64" -> token = "!" + original.substring(1);
            case "oversized" -> token = "A".repeat(100_000);
            case "wrongKey" -> {
                byte[] otherKey = testKey();
                otherKey[0] ^= 1;
                token = new HistoryCursorCodec(otherKey).encode(codec.decode(original));
            }
            default -> {
                if (defect.equals("unknownVersion")) {
                    payload[0] = 2;
                } else {
                    ByteBuffer.wrap(payload).putLong(41, 0);
                }
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(testKey(), "HmacSHA256"));
                var encoder = Base64.getUrlEncoder().withoutPadding();
                token = encoder.encodeToString(payload) + "." + encoder.encodeToString(mac.doFinal(payload));
            }
        }
        assertThatThrownBy(() -> history.history(source, new PageRequest(1, Optional.of(token))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid history cursor");
        assertThat(observed.boundReads.get()).isZero();
        assertThat(observed.pageReads.get()).isZero();
        assertThat(identities.calls).isEmpty();
    }

    @Test
    void crossAccountReplayCannotQueryEitherAccountsFacts() {
        String cursor = continuation();
        assertThatThrownBy(() -> history.history(destination, new PageRequest(1, Optional.of(cursor))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid history cursor");
        assertThat(identities.calls).isEmpty();
        assertThat(observed.pageReads.get()).isZero();
    }

    @Test
    void authenticatedCursorForAnotherCustomerOwnerCannotReadFinancialFacts() {
        var state = codec.decode(continuation());
        String otherOwner = codec.encode(new HistoryCursor(UUID.randomUUID(), source, state.highWater(), state.lastSeen(), state.limit()));
        assertThatThrownBy(() -> history.history(source, new PageRequest(1, Optional.of(otherOwner))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid history cursor");
        assertThat(identities.calls).containsOnlyKeys(source);
        assertThat(observed.boundReads.get()).isZero();
        assertThat(observed.pageReads.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 20, 100})
    void continuationCannotChangeThePageLimit(int limit) {
        String cursor = continuation();
        assertThatThrownBy(() -> history.history(source, new PageRequest(limit, Optional.of(cursor))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid history cursor");
        assertThat(identities.calls).isEmpty();
        assertThat(observed.pageReads.get()).isZero();
    }

    @Test
    void sameKeyServiceRecreationCanContinueAndDoesNotInstallAnIncompletePublicProvider() {
        String cursor = continuation();
        var expected = history.history(source, new PageRequest(1, Optional.of(cursor)));
        ledgerContext.registerBean("recreatedHistory", CustomerHistoryQueryService.class,
                () -> new CustomerHistoryQueryService(observed, identities, new HistoryCursorCodec(testKey())));
        var recreated = ledgerContext.getBean("recreatedHistory", ReadCustomerHistory.class);
        assertThat(AopUtils.isAopProxy(recreated)).isTrue();
        assertThat(recreated.history(source, new PageRequest(1, Optional.of(cursor)))).isEqualTo(expected);
        assertThat(ledgerContext.getBeansOfType(LedgerQueries.class)).isEmpty();
        assertThat(CustomerHistoryQueryService.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("receipt"));
    }
}
