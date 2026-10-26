package com.finfast.ledger.internal.application.usecase;

import com.finfast.ledger.api.LedgerQueries;
import com.finfast.ledger.internal.application.port.inbound.ReadCustomerHistory;
import com.finfast.ledger.internal.application.port.inbound.ReadCustomerReceipt;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Complete LedgerQueries provider; authorization remains owned by the History inbound boundary. */
public class LedgerQueriesService implements LedgerQueries {
    private final ReadCustomerHistory history;
    private final ReadCustomerReceipt receipts;

    public LedgerQueriesService(ReadCustomerHistory history, ReadCustomerReceipt receipts) {
        this.history = Objects.requireNonNull(history, "history");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
    }

    @Override
    public Page history(UUID participantAccountId, PageRequest request) {
        return history.history(participantAccountId, request);
    }

    @Override
    public Optional<Receipt> receipt(UUID participantAccountId, String reference) {
        return receipts.receipt(participantAccountId, reference);
    }
}
