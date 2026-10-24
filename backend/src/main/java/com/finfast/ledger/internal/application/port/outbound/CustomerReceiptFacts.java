package com.finfast.ledger.internal.application.port.outbound;

import com.finfast.ledger.internal.domain.entity.FinancialTransaction;

import java.util.Optional;
import java.util.UUID;

/** One immutable committed ordinary fact visible to a participating Customer. */
public interface CustomerReceiptFacts {
    Optional<FinancialTransaction> find(UUID participantAccountId, String reference);
}
