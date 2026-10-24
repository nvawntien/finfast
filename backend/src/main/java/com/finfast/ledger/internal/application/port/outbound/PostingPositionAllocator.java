package com.finfast.ledger.internal.application.port.outbound;

/** Reserve one tentative position after Accounts has applied an eligible pair in the caller's transaction. */
public interface PostingPositionAllocator {
    long reserveNext();
}
