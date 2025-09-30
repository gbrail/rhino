package org.mozilla.javascript.dtoa;

public class Decimal {
    private final long significand;
    private final int exponent;
    private final boolean negative;

    Decimal(long f, int e, boolean n) {
        this.significand = f;
        this.exponent = e;
        this.negative = n;
    }

    public long significand() {
        return significand;
    }

    public int exponent() {
        return exponent;
    }

    public boolean negative() {
        return negative;
    }

    @Override
    public String toString() {
        String unscaled = Long.toString(significand);
        StringBuilder s = new StringBuilder();
        if (negative) {
            s.append('-');
        }
        if (exponent < -5 || exponent > 21) {
            return toExponential(s, unscaled);
        }
        return toFixed(s, unscaled);
    }

    private String toFixed(StringBuilder s, String unscaled) {
        if (exponent == 0) {
            // Optimization to avoid a copy in the most common case
            if (!negative) {
                return unscaled;
            }
            s.append(unscaled);
        } else if (exponent >= unscaled.length()) {
            // Very big number with zeroes after the unscaled value
            s.append(unscaled);
            padZeroes(s, exponent - unscaled.length());
        } else if (exponent > 0) {
            // Decimal point in the middle
            s.append(unscaled.substring(0, exponent));
            s.append('.');
            s.append(trimTrailingZeroes(unscaled.substring(exponent)));
        } else {
            // A very small number that starts with zero
            s.append("0.");
            padZeroes(s, -exponent);
            s.append(trimTrailingZeroes(unscaled));
        }
        return s.toString();
    }

    private String toExponential(StringBuilder s, String unscaled) {
        s.append(unscaled.substring(0, 1));
        String remaining = trimTrailingZeroes(unscaled.substring(1));
        if (!remaining.isEmpty()) {
            s.append('.');
            s.append(remaining);
        }
        s.append('e');
        if (exponent > 0) {
            s.append('+');
            s.append(exponent - 1);
        } else if (exponent < 0) {
            s.append(exponent - 1);
        } else {
            s.append("+0");
        }
        return s.toString();
    }

    private static String trimTrailingZeroes(String s) {
        int len = s.length();
        for (int i = len; i > 0; i--) {
            if (s.charAt(i - 1) == '0') {
                len--;
            } else {
                break;
            }
        }
        if (len < s.length()) {
            return s.substring(0, len);
        }
        return s;
    }

    private static void padZeroes(StringBuilder b, int c) {
        for (int i = 0; i < c; i++) {
            b.append('0');
        }
    }
}
