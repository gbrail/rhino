package org.mozilla.javascript.v8dtoa;

import java.math.BigDecimal;

public class BigDecimalDtoA {

    public static String dtoa(BigDecimal d) {
        // Save negativity for later to make formatting easier
        boolean negative = d.compareTo(BigDecimal.ZERO) < 0;
        if (negative) {
            d = d.negate();
        }

        // Get the shortest representation of the unscaled value
        int precision = d.precision();
        int scale = d.scale();
        String unscaled = d.unscaledValue().toString();

        if (scale == 0) {
            // No decimals at all
            return integerEquivalent(d, unscaled, negative);
        } else if (scale > 0) {
            // Digits after the decimal point
            if (precision <= scale) {
                // And no digits before the decimal point
                return smallDecimal(d, unscaled, negative, precision, scale);
            }
            return normalDecimal(d, unscaled, negative, precision, scale);
        } else {
            // No digits after the decimal point, and in fact trailing zeroes before
            return largeDecimal(d, unscaled, negative, precision, scale);
        }
    }

    private static String integerEquivalent(BigDecimal d, String unscaled, boolean negative) {
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        buf.append(unscaled);
        return buf.toString();
    }

    private static String normalDecimal(
            BigDecimal d, String unscaled, boolean negative, int precision, int scale) {
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        int wholePart = precision - scale;
        buf.append(unscaled.substring(0, wholePart));
        String decimalPart = stripTrailingZeroes(unscaled.substring(wholePart));
        if (!decimalPart.isEmpty()) {
            buf.append('.');
            buf.append(decimalPart);
        }
        return buf.toString();
    }

    private static String smallDecimal(
            BigDecimal d, String unscaled, boolean negative, int precision, int scale) {
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        int zeroes = scale - precision;
        if (zeroes > 5) {
            // Go exponential
            buf.append(unscaled.substring(0, 1));
            String decimalPart = stripTrailingZeroes(unscaled.substring(1));
            if (!decimalPart.isEmpty()) {
                buf.append('.');
                buf.append(decimalPart);
            }
            buf.append("e-");
            buf.append(zeroes + 1);
        } else {
            // Just decimal
            buf.append("0.");
            for (int i = 0; i < scale - precision; i++) {
                buf.append('0');
            }
            buf.append(unscaled);
        }
        return buf.toString();
    }

    private static String largeDecimal(
            BigDecimal d, String unscaled, boolean negative, int precision, int scale) {
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        int zeroes = -scale;
        if (zeroes > 5) {
            // Go exponential
            buf.append(unscaled.substring(0, 1));
            String decimalPart = stripTrailingZeroes(unscaled.substring(1));
            if (!decimalPart.isEmpty()) {
                buf.append('.');
                buf.append(decimalPart);
            }
            buf.append("e+");
            buf.append(zeroes + precision - 1);
        } else {
            // Just decimal, with extra zeroes
            buf.append(unscaled);
            for (int i = 0; i < zeroes; i++) {
                buf.append('0');
            }
        }
        return buf.toString();
    }

    private static String stripTrailingZeroes(String s) {
        int lastZero = s.length();
        while (lastZero >= 1 && s.charAt(lastZero - 1) == '0') {
            lastZero--;
        }
        if (lastZero < s.length()) {
            return s.substring(0, lastZero);
        }
        return s;
    }
}
