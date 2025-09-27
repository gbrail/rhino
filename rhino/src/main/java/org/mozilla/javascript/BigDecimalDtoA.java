package org.mozilla.javascript;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalDtoA {

    private static final double TEN_TO_21 = Math.pow(10.0, 21.0);

    /**
     * Implement Number::toString as specified in ECMAScript under "abstract
     * operations." Currently only implemented for radix 10.
     */
    public static String numberToString(double v) {
        if (Double.isNaN(v)) {
            return "NaN";
        }
        if (v == 0.0 || v == ScriptRuntime.negativeZero) {
            return "0";
        }
        if (Double.isInfinite(v)) {
            return "Infinity";
        }

        BigDecimal d = new BigDecimal(v, MathContext.DECIMAL64);

        // Save negativity for later to make formatting easier
        boolean negative = d.compareTo(BigDecimal.ZERO) < 0;
        if (negative) {
            d = d.negate();
        }

        int precision = d.precision();
        int scale = d.scale();
        // Subsequent operations are string manipulations on this
        String unscaled = d.unscaledValue().toString();

        if (scale >= 0) {
            if (precision <= scale) {
                if ((scale - precision) > 5) {
                    return smallDecimalExponential(d, unscaled, negative, precision, scale);
                }
                return smallDecimal(d, unscaled, negative, precision, scale);
            }
            return normalDecimal(d, unscaled, negative, precision, scale);
        } else {
            if (scale < -5) {
                return largeDecimalExponential(d, unscaled, negative, precision, scale);
            }
            return largeDecimal(d, unscaled, negative, scale);
        }
    }

    /**
     * Implement Number::toFixed as specified in ECMAScript for the Number
     * prototype.
     */
    public static String numberToStringFixed(double v, int fractionDigits) {
        if (!Double.isFinite(v) || v > TEN_TO_21) {
            return numberToString(v);
        }

        BigDecimal d = new BigDecimal(v, MathContext.DECIMAL64);
        boolean negative = d.compareTo(BigDecimal.ZERO) < 0;
        if (negative) {
            d = d.negate();
        }

        int scale = fractionDigits;
        d = d.setScale(scale, RoundingMode.HALF_EVEN);
        int precision = d.precision();
        String unscaled = d.unscaledValue().toString();
        throw new AssertionError("unimplemented");
    }

    /**
     * A decimal number that is >= 1 in magnitude, and does not require exponents
     * or extra padding to represent the unscaled value.
     */
    private static String normalDecimal(
            BigDecimal d, String unscaled, boolean negative, int precision, int scale) {
        assert scale >= 0;
        assert d.compareTo(BigDecimal.ONE) >= 0;
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

    /**
     * A decimal with many fraction digits that is smaller than zero.
     */
    private static String smallDecimal(
            BigDecimal d, String unscaled, boolean negative, int precision, int scale) {
        assert scale >= precision;
        assert d.compareTo(BigDecimal.ONE) < 0;
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        int zeroes = scale - precision;
        buf.append("0.");
        for (int i = 0; i < zeroes; i++) {
            buf.append('0');
        }
        buf.append(unscaled);
        return buf.toString();
    }

    /**
     * A decimal with many fraction digits that is smaller than zero, output in
     * exponential notation.
     */
    private static String smallDecimalExponential(
            BigDecimal d, String unscaled, boolean negative, int precision, int scale) {
        assert scale >= precision;
        assert d.compareTo(BigDecimal.ONE) < 0;
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        int zeroes = scale - precision;
        buf.append(unscaled.substring(0, 1));
        String decimalPart = stripTrailingZeroes(unscaled.substring(1));
        if (!decimalPart.isEmpty()) {
            buf.append('.');
            buf.append(decimalPart);
        }
        buf.append("e-");
        buf.append(zeroes + 1);
        return buf.toString();
    }

    /**
     * A decimal with no fraction digits that is larger than just the
     * unscaled value.
     */
    private static String largeDecimal(
            BigDecimal d, String unscaled, boolean negative, int scale) {
        assert scale < 0;
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        int zeroes = -scale;
        buf.append(unscaled);
        for (int i = 0; i < zeroes; i++) {
            buf.append('0');
        }
        return buf.toString();
    }

    /**
     * A decimal with no fraction digits that is larger than just the
     * unscaled value, output with exponential notation.
     */
    private static String largeDecimalExponential(
            BigDecimal d, String unscaled, boolean negative, int precision, int scale) {
        assert scale < 0;
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        int zeroes = -scale;
        buf.append(unscaled.substring(0, 1));
        String decimalPart = stripTrailingZeroes(unscaled.substring(1));
        if (!decimalPart.isEmpty()) {
            buf.append('.');
            buf.append(decimalPart);
        }
        buf.append("e+");
        buf.append(zeroes + precision - 1);
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
