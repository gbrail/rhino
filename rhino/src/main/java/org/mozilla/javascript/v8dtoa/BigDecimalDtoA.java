package org.mozilla.javascript.v8dtoa;

import java.math.BigDecimal;

public class BigDecimalDtoA {

    public static String dtoa(BigDecimal d) {
        boolean negative = d.compareTo(BigDecimal.ZERO) < 0;
        if (negative) {
            d = d.negate();
        }

        String unscaled = d.unscaledValue().toString();
        if (d.scale() == 0) {
            return integerEquivalent(d, unscaled, negative);
        } else if (d.scale() > 0) {
            if (d.precision() <= d.scale()) {
                return smallDecimal(d, unscaled, negative);
            }
            return normalDecimal(d, unscaled, negative);
        } else {
            return largeDecimal(d, unscaled, negative);
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

    private static String normalDecimal(BigDecimal d, String unscaled, boolean negative) {
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        // TODO trim trailing zeroes
        int wholePart = d.precision() - d.scale();
        buf.append(unscaled.substring(0, wholePart));
        buf.append('.');
        buf.append(unscaled.substring(wholePart));
        return buf.toString();
    }

    private static String smallDecimal(BigDecimal d, String unscaled, boolean negative) {
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        int zeroes = d.scale() - d.precision();
        if (zeroes > 5) {
            // Go exponential
            buf.append(unscaled.substring(0, 1));
            buf.append('.');
            buf.append(unscaled.substring(1));
            buf.append("e-");
            buf.append(zeroes + 1);
        } else {
            // Just decimal
            buf.append("0.");
            for (int i = 0; i < d.scale() - d.precision(); i++) {
                buf.append('0');
            }
            buf.append(unscaled);
        }
        return buf.toString();
    }

    private static String largeDecimal(BigDecimal d, String unscaled, boolean negative) {
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        int zeroes = -d.scale();
        if (zeroes > 5) {
            // Go exponential
            buf.append(unscaled.substring(0, 1));
            buf.append('.');
            buf.append(unscaled.substring(1));
            buf.append("e+");
            buf.append(zeroes + d.precision() - 1);
        } else {
            // Just decimal, with extra zeroes
            buf.append(unscaled);
            for (int i = 0; i < zeroes; i++) {
                buf.append('0');
            }
        }
        return buf.toString();
    }
}
