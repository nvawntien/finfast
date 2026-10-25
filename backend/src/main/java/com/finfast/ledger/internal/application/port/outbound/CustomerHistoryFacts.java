package com.finfast.ledger.internal.application.port.outbound;

import com.finfast.ledger.internal.domain.entity.FinancialTransaction;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/** Ledger facts in the caller's committed read-only snapshot. No Account SQL or identity resolution. */
public interface CustomerHistoryFacts {
    long highWater();

    /** Newest positions first; transactionLimit includes one lookahead transaction with its complete entries. */
    List<FinancialTransaction> page(UUID participantAccountId, long highWater, OptionalLong beforeExclusive, int transactionLimit);
}
