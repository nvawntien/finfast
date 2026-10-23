package com.finfast.accounts.api;

import java.util.Comparator;
import java.util.List;

/**
 * TD-016-AM-03 APPROVED — V1 (2026-10-23). Accounts-owned; Administration only.
 * Caller establishes current ADMIN authorization before invocation/disclosure.
 * One consistent committed read-only snapshot, excluding caller pending writes and all Treasury rows.
 * Empty is a valid no-Customer snapshot; SQL/invariant failures propagate, never become an empty listing.
 * Administration resolves Customer display names through CustomerQueries. No paging/filtering contract is added.
 * Listing preserves all rows, ordered by exact Account Number using Java String ordering; no silent truncation.
 */
public interface AdminAccountQueries {
    Listing listCustomers();

    record Listing(List<CustomerAccount> items) {
        public Listing {
            items = List.copyOf(items).stream()
                    .sorted(Comparator.comparing(account -> account.number().value()))
                    .toList();
        }
    }
}
