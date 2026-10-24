package com.finfast.ledger.internal.application.port.inbound;

import com.finfast.ledger.api.LedgerQueries.Page;
import com.finfast.ledger.api.LedgerQueries.PageRequest;

import java.util.UUID;

/** Ledger-local BK-014 entry. Future LedgerQueries composition delegates here once BK-015 is available. */
public interface ReadCustomerHistory {
    Page history(UUID participantAccountId, PageRequest request);
}
