package com.finfast.ledger.api;

import com.finfast.common.money.VndAmount;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** History's committed CUSTOMER movement projection; provider owner: Tiến.
 * History establishes current CUSTOMER ownership before calling and on each page.
 * Provider filters by participant Account; exclude GENESIS, rejections and pending
 * writes. First bound/page must share a consistent committed snapshot, without
 * holding a DB transaction between pages. Cursor encoding/order algorithm remains
 * TD-004 review work. No Admin-wide/raw-entry projection is frozen in BK-003.
 */
public interface LedgerQueries {
    Page history(UUID participantAccountId, PageRequest request);

    Optional<Receipt> receipt(UUID participantAccountId, String reference);

    record PageRequest(int limit, Optional<String> cursor) {
        public PageRequest {
            if (limit < 1 || limit > 100) {
                throw new IllegalArgumentException("History limit must be between 1 and 100");
            }
            Objects.requireNonNull(cursor, "cursor");
        }

        public static PageRequest firstPage() {
            return new PageRequest(20, Optional.empty());
        }
    }

    /** Copy the collection; consumers cannot mutate a provider's projection. */
    record Page(List<Movement> items, Optional<String> nextCursor) {
        public Page {
            items = List.copyOf(items);
            Objects.requireNonNull(nextCursor, "nextCursor");
        }
    }

    enum Type { INTERNAL_TRANSFER, DEMO_ALLOCATION }
    enum Direction { INCOMING, OUTGOING }

    /** VND is implicit; labels disclose only permitted counterparty information. */
    record Movement(String reference, Type type, Direction direction, VndAmount amount,
                    String counterpartyLabel, Instant recordedAt) {
        public Movement {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(amount, "amount").requirePositive();
            Objects.requireNonNull(counterpartyLabel, "counterpartyLabel");
            Objects.requireNonNull(recordedAt, "recordedAt");
        }
    }

    /** Retrieved immutable committed facts; unavailable/nonparticipant references are empty. */
    record Receipt(String reference, Type type, VndAmount amount,
                   String sourceLabel, String destinationLabel, Instant recordedAt) {
        public Receipt {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(amount, "amount").requirePositive();
            Objects.requireNonNull(sourceLabel, "sourceLabel");
            Objects.requireNonNull(destinationLabel, "destinationLabel");
            Objects.requireNonNull(recordedAt, "recordedAt");
        }
    }
}
