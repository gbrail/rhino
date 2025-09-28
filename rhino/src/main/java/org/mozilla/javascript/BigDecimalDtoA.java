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
        if (!Double.isFinite(v)) {
            return ScriptRuntime.toString(v);
        }
        return numberToString(new BigDecimal(v, MathContext.DECIMAL64));
    }

    public static String numberToString(BigDecimal d) {
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
                    return smallDecimalExponential(d, unscaled, negative, precision, scale, true);
                }
                return smallDecimal(d, unscaled, negative, precision, scale);
            }
            return normalDecimal(d, unscaled, negative, precision, scale, true);
        } else {
            if (scale < -5) {
                return largeDecimalExponential(d, unscaled, negative, precision, scale, true);
            }
            return largeDecimal(d, unscaled, negative, scale);
        }
    }

    /**
     * Implement Number.prototype.toFixed as specified in ECMAScript for the Number
     * prototype.
     */
    public static String numberToStringFixed(double v, int fractionDigits) {
        if (!Double.isFinite(v) || v >= TEN_TO_21) {
            return numberToString(v);
        }

        // The spec mentions in a note that toFixed prints more significant
        // digits than toString, hence "unlimited" here.
        BigDecimal d = new BigDecimal(v, MathContext.UNLIMITED);
        boolean negative = d.compareTo(BigDecimal.ZERO) < 0;
        if (negative) {
            d = d.negate();
        }

        int scale = fractionDigits;
        // This can round and reduce precision, or increase scale and add zeroes.
        d = d.setScale(scale, RoundingMode.HALF_EVEN);
        int precision = d.precision();
        String unscaled = d.unscaledValue().toString();

        if (scale >= 0) {
            if (precision <= scale) {
                return smallDecimal(d, unscaled, negative, precision, scale);
            }
            return normalDecimal(d, unscaled, negative, precision, scale, false);
        } else {
            return largeDecimal(d, unscaled, negative, scale);
        }
    }

    /**
     * Implement Number.prototype.toExponential as specified in ECMAScript for the
     * Number prototype. If fraction digits was undefined then pass -1;
     */
    public static String numberToStringExponential(double v, int fractionDigits) {
        if (!Double.isFinite(v)) {
            return numberToString(v);
        }
        BigDecimal d = new BigDecimal(v, MathContext.UNLIMITED);
        boolean negative = d.compareTo(BigDecimal.ZERO) < 0;
        if (negative) {
            d = d.negate();
        }

        if (d.equals(BigDecimal.ZERO)) {
            return exponentialZero(fractionDigits);
        }

        if (fractionDigits >= 0) {
            // Round or extend the value depending on the request.
            d = d.round(new MathContext(fractionDigits + 1, RoundingMode.HALF_UP));
        }

        int scale = d.scale();
        int precision = d.precision();
        String unscaled = d.unscaledValue().toString();

        if (d.compareTo(BigDecimal.ONE) >= 0) {
            return largeDecimalExponential(d, unscaled, negative, precision, scale, false);
        }
        return smallDecimalExponential(d, unscaled, negative, precision, scale, false);
    }

    /**
     * Special handling for zero as shown in the spec for toExponential.
     */
    private static String exponentialZero(int fractionDigits) {
        if (fractionDigits < 0) {
            return "0e+0";
        } else {
            StringBuilder s = new StringBuilder();
            s.append('0');
            if (fractionDigits > 0) {
                s.append('.');
            }
            for (int i = 0; i < fractionDigits; i++) {
                s.append('0');
            }
            s.append("e+0");
            return s.toString();
        }
    }

    /**
     * A decimal number that is >= 1 in magnitude, and does not require exponents
     * or extra padding to represent the unscaled value.
     */
    private static String normalDecimal(
            BigDecimal d, String unscaled, boolean negative, int precision, int scale, boolean strip) {
        assert scale >= 0;
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        int wholePart = precision - scale;
        buf.append(unscaled.substring(0, wholePart));
        String decimalPart = unscaled.substring(wholePart);
        if (strip) {
            decimalPart = stripTrailingZeroes(decimalPart);
        }
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
     * A decimal with an absolute value smaller than one, represented in
     * exponential notation.
     */
    private static String smallDecimalExponential(
            BigDecimal d, String unscaled, boolean negative, int precision, int scale, boolean strip) {
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        int zeroes = scale - precision;
        buf.append(unscaled.substring(0, 1));
        String decimalPart = unscaled.substring(1);
        if (strip) {
            decimalPart = stripTrailingZeroes(decimalPart);
        }
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
     * A decimal that is larger than one, represented in exponential notation.
     */
    private static String largeDecimalExponential(
            BigDecimal d, String unscaled, boolean negative, int precision, int scale, boolean strip) {
        var buf = new StringBuilder();
        if (negative) {
            buf.append('-');
        }
        int zeroes = -scale;
        buf.append(unscaled.substring(0, 1));
        String decimalPart = unscaled.substring(1);
        if (strip) {
            decimalPart = stripTrailingZeroes(decimalPart);
        }
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
