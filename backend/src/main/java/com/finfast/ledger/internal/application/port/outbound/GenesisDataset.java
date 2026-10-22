package com.finfast.ledger.internal.application.port.outbound;

import com.finfast.ledger.internal.domain.entity.FinancialTransaction;

/** Controlled GENESIS linkage in the caller's transaction, not a public module capability. */
public interface GenesisDataset {
    void requireUninitialized();

    void insertGenesis(FinancialTransaction genesis);
}
