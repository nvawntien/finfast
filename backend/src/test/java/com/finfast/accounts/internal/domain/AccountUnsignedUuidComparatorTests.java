package com.finfast.accounts.internal.domain;

import com.finfast.accounts.internal.domain.service.AccountUnsignedUuidComparator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountUnsignedUuidComparatorTests {

    @Test
    void verifiesUnsignedComparisonDiffersFromJavaSignedComparisonAtSignBit() {
        // High bit set: in Java signed long this is negative (Long.MIN_VALUE)
        UUID highBitSet = new UUID(0x8000_0000_0000_0000L, 0L);
        // High bit cleared: in Java signed long this is positive (Long.MAX_VALUE)
        UUID highBitCleared = new UUID(0x7FFF_FFFF_FFFF_FFFFL, 0L);

        // Java default signed compareTo treats highBitSet < highBitCleared
        assertThat(highBitSet.compareTo(highBitCleared)).isNegative();

        // PostgreSQL 16-byte unsigned memcmp treats 0x80 > 0x7F
        // TD-002 requires unsigned comparison:
        int cmp = AccountUnsignedUuidComparator.compareUnsigned(highBitSet, highBitCleared);
        assertThat(cmp).isPositive();

        int reverse = AccountUnsignedUuidComparator.compareUnsigned(highBitCleared, highBitSet);
        assertThat(reverse).isNegative();
    }

    @Test
    void comparesLeastSignificantBitsWhenMostSignificantBitsAreEqual() {
        UUID a = new UUID(0x0000_0000_0000_0001L, 0x8000_0000_0000_0000L);
        UUID b = new UUID(0x0000_0000_0000_0001L, 0x7FFF_FFFF_FFFF_FFFFL);

        assertThat(a.compareTo(b)).isNegative();
        assertThat(AccountUnsignedUuidComparator.compareUnsigned(a, b)).isPositive();
    }

    @Test
    void equalsReturnsZero() {
        UUID u = UUID.randomUUID();
        assertThat(AccountUnsignedUuidComparator.compareUnsigned(u, u)).isZero();
        assertThat(AccountUnsignedUuidComparator.INSTANCE.compare(u, u)).isZero();
    }
}
