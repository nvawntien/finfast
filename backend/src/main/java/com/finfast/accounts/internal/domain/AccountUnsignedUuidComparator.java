package com.finfast.accounts.internal.domain;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * TD-002: Deterministic 16-byte unsigned lexicographic comparator for UUIDs.
 * Compares most-significant 64-bit part unsigned, then least-significant 64-bit part unsigned.
 * Mathematically equivalent to PostgreSQL's 16-byte unsigned {@code memcmp} comparator,
 * preventing deadlock cycles when locking multiple accounts in a transaction.
 */
public final class AccountUnsignedUuidComparator implements Comparator<UUID> {

    public static final AccountUnsignedUuidComparator INSTANCE = new AccountUnsignedUuidComparator();

    private AccountUnsignedUuidComparator() {
    }

    @Override
    public int compare(UUID a, UUID b) {
        return compareUnsigned(a, b);
    }

    public static int compareUnsigned(UUID a, UUID b) {
        Objects.requireNonNull(a, "first UUID");
        Objects.requireNonNull(b, "second UUID");
        if (a.equals(b)) {
            return 0;
        }
        int cmp = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        if (cmp != 0) {
            return cmp;
        }
        return Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
