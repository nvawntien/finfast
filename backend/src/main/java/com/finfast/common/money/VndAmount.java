package com.finfast.common.money;

/** Whole, nonnegative VND, bounded by PostgreSQL BIGINT. Not a signed ledger delta. */
public record VndAmount(long value) {

    private static final String INVALID_INPUT = "VND must be a canonical nonnegative decimal string within signed 64-bit range";
    private static final String MAX_DECIMAL = Long.toString(Long.MAX_VALUE);

    public VndAmount {
        if (value < 0) {
            throw new IllegalArgumentException("VND amount must be nonnegative");
        }
    }

    public static VndAmount parse(String decimal) {
        // Reject oversized input before scanning/parsing it; BIGINT has at most 19 digits.
        if (decimal == null || decimal.isEmpty() || decimal.length() > 19
                || (decimal.length() > 1 && decimal.charAt(0) == '0')) {
            throw new IllegalArgumentException(INVALID_INPUT);
        }
        for (int index = 0; index < decimal.length(); index++) {
            char digit = decimal.charAt(index);
            if (digit < '0' || digit > '9') {
                throw new IllegalArgumentException(INVALID_INPUT);
            }
        }
        if (decimal.length() == MAX_DECIMAL.length() && decimal.compareTo(MAX_DECIMAL) > 0) {
            throw new IllegalArgumentException(INVALID_INPUT);
        }
        // Canonical digits and range are already checked, so parsing cannot echo invalid input.
        return new VndAmount(Long.parseLong(decimal));
    }

    public VndAmount requirePositive() {
        if (value == 0) {
            throw new IllegalArgumentException("VND amount must be positive");
        }
        return this;
    }

    public VndAmount plus(VndAmount other) {
        return new VndAmount(VndArithmetic.addExact(value, other.value));
    }

    public VndAmount minus(VndAmount other) {
        return new VndAmount(VndArithmetic.subtractExact(value, other.value));
    }

    public String toDecimalString() {
        return Long.toString(value);
    }
}
