package com.finfast.idempotency.api;

import com.finfast.common.money.VndAmount;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Transfers/Administration scoped coordination and final-result contract; owner: Minh.
 * Authorize the server-established initiator/operation/resources before every
 * inspect, lookup or replay. No dependency on Identity/Ledger business types.
 * inspect and record require the SAME existing outer PostgreSQL transaction:
 * coordinate full scope, then read a fresh authoritative snapshot before deciding
 * Execute/Replay/Conflict. Coordination lasts until commit/rollback. No lock
 * algorithm, digest format, persisted PROCESSING claim or retry budget is selected.
 * Technical failures escape; retry outside the proxy in a fresh transaction with
 * the original key. Never turn them into Rejection. No TTL/key recycling.
 */
public interface RequestOutcomes {
    Decision inspect(Scope scope, CanonicalIntent intent);

    /** Only after Execute; completion joins posting + required audit, rejection has zero postings.
     * Insert once and never replace a finalized scope. Return is not commit evidence.
     */
    void record(Scope scope, CanonicalIntent intent, FinalOutcome outcome);

    /** Authorized committed lookup only; absence is UNKNOWN, never proof of failure or permission to execute. */
    Optional<FinalOutcome> find(Scope scope);

    enum Operation { INTERNAL_TRANSFER, DEMO_ALLOCATION }

    /** Exact opaque key, including case/whitespace; same 1..200 characters as the existing schema/header. */
    record Key(String value) {
        public Key {
            Objects.requireNonNull(value, "value");
            if (value.isEmpty() || value.codePointCount(0, value.length()) > 200) {
                throw new IllegalArgumentException("Idempotency key must contain 1 to 200 characters");
            }
        }
    }

    record Scope(UUID initiatorUserId, Operation operation, Key key) {
        public Scope {
            Objects.requireNonNull(initiatorUserId, "initiatorUserId");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(key, "key");
        }
    }

    /** Versioned canonical JSON document, not raw HTTP, a hash or a persistence entity.
     * Its producer must supply validated deterministic JSON with all original
     * intent-bearing fields (including invalid numeric tokens, absent/empty note,
     * exact reason). Compare full versioned intent, not only a digest.
     * The v1 encoder/validation/test vectors remain TD-003 owner review work.
     * Never include credentials, cookies, mutable balances/status or private logs.
     */
    record CanonicalIntent(short version, String json) {
        public CanonicalIntent {
            Objects.requireNonNull(json, "json");
            if (version <= 0 || json.isBlank()) {
                throw new IllegalArgumentException("Canonical intent requires a positive version and JSON document");
            }
        }
    }

    sealed interface Decision permits Execute, Replay, Conflict, NotFinal { }
    record Execute() implements Decision { }
    record Replay(FinalOutcome outcome) implements Decision {
        public Replay {
            Objects.requireNonNull(outcome, "outcome");
        }
    }
    record Conflict() implements Decision { }

    /** PROCESSING requires evidence of this exact scope; unrelated lock contention is UNKNOWN. */
    enum NotFinal implements Decision { PROCESSING, UNKNOWN }

    /** Intended final value until outer commit; only committed lookup/replay establishes durability.
     * These typed fields form the stable V1 response snapshot, not current balances.
     * No generic HTTP/Jackson response or technical/non-final variant is persisted.
     */
    sealed interface FinalOutcome permits Completion, Rejection { }
    record Completion(UUID transactionId, String reference, VndAmount amount,
                      Instant recordedAt) implements FinalOutcome {
        public Completion {
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(amount, "amount").requirePositive();
            Objects.requireNonNull(recordedAt, "recordedAt");
        }
    }
    record Rejection(String code, String publicMessage) implements FinalOutcome {
        public Rejection {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(publicMessage, "publicMessage");
            if (code.isBlank() || publicMessage.isBlank()) {
                throw new IllegalArgumentException("Rejection requires a stable code and safe message");
            }
        }
    }
}
