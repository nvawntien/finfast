package com.finfast.ledger.internal.application.port.outbound;

import com.finfast.ledger.internal.domain.entity.FinancialTransaction;

/** Ledger-owned write port; joins the existing transaction and never acknowledges a commit. */
public interface FinancialTransactionStore {
    void insert(FinancialTransaction transaction);
}
