package com.finfast.ledger.internal.application.port.outbound;

import com.finfast.ledger.internal.domain.entity.FinancialTransaction;

import java.util.List;

/** Complete validated Ledger facts, newest posting position first, in the caller's read-only snapshot. */
public interface CommittedFinancialTransactions {
    List<FinancialTransaction> listCommitted();
}
