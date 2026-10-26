package com.finfast.ledger.internal.application.port.inbound;

import com.finfast.ledger.api.LedgerQueries.Receipt;

import java.util.Optional;
import java.util.UUID;

/** Ledger-local BK-015 entry for an immutable participant receipt. */
public interface ReadCustomerReceipt {
    Optional<Receipt> receipt(UUID participantAccountId, String reference);
}
