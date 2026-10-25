package com.finfast.customer.internal.application.port.outbound;

import java.util.UUID;

/** Profile write joins registration; Identity has already persisted the CUSTOMER User. */
public interface CustomerProfiles {
    void insert(UUID customerId, UUID userId, String displayName);
}
