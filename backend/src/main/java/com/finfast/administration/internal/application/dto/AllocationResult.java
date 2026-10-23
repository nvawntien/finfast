package com.finfast.administration.internal.application.dto;

import com.finfast.idempotency.api.RequestOutcomes;

import java.util.Objects;

/** Internal application results, never HTTP commit acknowledgements.
 * A new outcome is Pending until the OUTERMOST transaction proxy completes successfully.
 * REQUIRED may join a caller transaction, so even return from this service's proxy can be tentative.
 */
public sealed interface AllocationResult {
    record Pending(RequestOutcomes.FinalOutcome outcome) implements AllocationResult {
        public Pending {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** The original final snapshot supplied by the authorized Idempotency provider. */
    record Replay(RequestOutcomes.FinalOutcome outcome) implements AllocationResult {
        public Replay {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    record Conflict() implements AllocationResult { }

    enum NotFinal implements AllocationResult { PROCESSING, UNKNOWN }
}
