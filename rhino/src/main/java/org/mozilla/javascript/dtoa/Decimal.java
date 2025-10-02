package org.mozilla.javascript.dtoa;

import java.math.BigDecimal;

public class Decimal {
    private final String digits;
    private final int exponent;
    private final boolean negative;
    private final boolean zero;

    Decimal(long f, int e, boolean n) {
        this.digits = Long.toString(f);
        this.zero = f == 0;
        this.exponent = e;
        this.negative = n;
    }

    Decimal(BigDecimal bd, int e, boolean n) {
        this.digits = bd.unscaledValue().toString();
        this.zero = bd.signum() == 0;
        this.exponent = e;
        this.negative = n;
    }

    public String digits() {
        return digits;
    }

    public int exponent() {
        return exponent;
    }

    public boolean negative() {
        return negative;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        if (negative) {
            s.append('-');
        }
        if (exponent < -5 || exponent > 21) {
            return toExponential(s, -1);
        }
        return toShortestFixed(s);
    }

    public String toFixedString(int fractionDigits) {
        StringBuilder s = new StringBuilder();
        if (negative) {
            s.append('-');
        }
        return toFixed(s, fractionDigits);
    }

    public String toFixedPrecisionString(int precision) {
        if (exponent >= 0) {
            // Positive exponent, potentially pad fraction digits
            return toFixedString(precision - exponent);
        }
        return toFixedString(precision);
    }

    public String toExponentialString(int fractionDigits) {
        StringBuilder s = new StringBuilder();
        if (negative) {
            s.append('-');
        }
        return toExponential(s, fractionDigits);
    }

    private String toShortestFixed(StringBuilder s) {
        if (exponent == 0) {
            // Optimization to avoid a copy in the most common case
            if (!negative) {
                return digits;
            }
            s.append(digits);
        } else if (exponent >= digits.length()) {
            // Very big number with zeroes after the digits value
            s.append(digits);
            padZeroes(s, exponent - digits.length());
        } else if (exponent > 0) {
            // Decimal point in the middle
            s.append(digits.substring(0, exponent));
            s.append('.');
            s.append(trimTrailingZeroes(digits.substring(exponent)));
        } else {
            // A very small number that starts with zero
            s.append("0.");
            padZeroes(s, -exponent);
            s.append(trimTrailingZeroes(digits));
        }
        return s.toString();
    }

    private String toFixed(StringBuilder s, int fractionDigits) {
        if (exponent == 0) {
            s.append(digits);
            if (fractionDigits > 0) {
                s.append('.');
                padZeroes(s, fractionDigits);
            }
        } else if (exponent >= digits.length()) {
            // Very big number with zeroes after the digits value
            s.append(digits);
            padZeroes(s, exponent - digits.length());
            if (fractionDigits > 0) {
                s.append('.');
                padZeroes(s, fractionDigits);
            }
        } else if (exponent > 0) {
            // Decimal point in the middle
            s.append(digits.substring(0, exponent));
            if (fractionDigits > 0) {
                s.append('.');
                String remaining = digits.substring(exponent);
                s.append(remaining);
                padZeroes(s, fractionDigits - remaining.length());
            }
        } else {
            // A very small number that starts with zero
            if (fractionDigits == 0) {
                return "0";
            }
            s.append("0.");
            padZeroes(s, -exponent);
            s.append(digits);
            padZeroes(s, fractionDigits - digits.length() - -exponent);
        }
        return s.toString();
    }

    private String toExponential(StringBuilder s, int fractionDigits) {
        if (zero) {
            s.append('0');
            if (fractionDigits > 0) {
                s.append('.');
                padZeroes(s, fractionDigits);
            }
            s.append("e+0");
            return s.toString();
        }

        s.append(digits.substring(0, 1));
        String remaining;
        int pad = 0;
        if (fractionDigits == 0) {
            remaining = "";
        } else if (fractionDigits < 0) {
            // Want most compact representation
            remaining = trimTrailingZeroes(digits.substring(1));
        } else {
            remaining = digits.substring(1);
            pad = fractionDigits - remaining.length();
        }
        if (!remaining.isEmpty() || pad > 0) {
            s.append('.');
            s.append(remaining);
            padZeroes(s, pad);
        }
        s.append('e');
        if (exponent > 0) {
            s.append('+');
            s.append(exponent - 1);
        } else {
            s.append(exponent - 1);
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
