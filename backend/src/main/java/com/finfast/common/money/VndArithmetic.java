package com.finfast.common.money;

/** Exact signed VND arithmetic. Callers retain balance and posting invariants. */
public final class VndArithmetic {

    private VndArithmetic() {
    }

    public static long addExact(long left, long right) {
        return Math.addExact(left, right);
    }

    public static long subtractExact(long left, long right) {
        return Math.subtractExact(left, right);
    }

    public static long negateExact(long value) {
        return Math.negateExact(value);
    }
}
