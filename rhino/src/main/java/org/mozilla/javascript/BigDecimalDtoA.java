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
        // ECMAScript section 6 says that numbers correspond to the "binary64"
        // standard, and that the "round ties to even" mode should be used.
        return numberToString(new BigDecimal(v, MathContext.DECIMAL64));
    }

    public static String numberToString(BigDecimal d) {
        // Save negativity for later to make formatting easier
        StringBuilder s = new StringBuilder();
        if (d.compareTo(BigDecimal.ZERO) < 0) {
            d = d.negate();
            s.append('-');
        }

        d = d.stripTrailingZeros();
        int precision = d.precision();
        int scale = d.scale();
        // Subsequent operations are string manipulations on this
        String unscaled = d.unscaledValue().toString();
        int exponent = calculateExponent(d, precision, scale);

        if ((exponent >= -5) && (exponent < 21)) {
            // Straight decimal notation
            if (scale < 0) {
                // Very big number with no decimal point
                assert exponent > 0;
                s.append(unscaled);
                for (int i = scale; i < 0; i++) {
                    s.append('0');
                }
            } else if (precision <= scale) {
                // Very small number less than zero
                assert exponent < 0;
                s.append("0.");
                for (int i = exponent; i >= 0; i--) {
                    s.append('0');
                }
                s.append(stripTrailingZeroes(unscaled));
            } else {
                // A fairly normal number
                assert precision > scale;
                s.append(unscaled.substring(0, precision - scale));
                // String remaining = stripTrailingZeroes(unscaled.substring(precision -
                // scale));
                String remaining = unscaled.substring(precision - scale);
                if (!remaining.isEmpty()) {
                    s.append('.');
                    s.append(remaining);
                }
            }
        } else {
            // Exponential notation
            s.append(unscaled.substring(0, 1));
            // String remaining = stripTrailingZeroes(unscaled.substring(1));
            String remaining = unscaled.substring(1);
            if (!remaining.isEmpty()) {
                s.append('.');
                s.append(remaining);
            }
            s.append('e');
            if (exponent > 0) {
                s.append('+');
            }
            s.append(exponent);
        }
        return s.toString();
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
     * Number prototype. If fraction digits was undefined then pass -1.
     */
    public static String numberToStringExponential(double v, int fractionDigits) {
        if (!Double.isFinite(v) || fractionDigits < 0) {
            return ScriptRuntime.toString(v);
        }
        // This is one of the functions that expects to work with
        // more precision than toString()
        BigDecimal d = new BigDecimal(v, MathContext.UNLIMITED);
        boolean negative = d.compareTo(BigDecimal.ZERO) < 0;
        if (negative) {
            d = d.negate();
        }
        if (d.equals(BigDecimal.ZERO)) {
            return exponentialZero(fractionDigits);
        }

        // Reduce precision if necessary. Test262 tests round "half up" which
        // is different from the rounding mode used in toString()
        d = d.round(new MathContext(fractionDigits + 1, RoundingMode.HALF_UP));

        int precision = d.precision();
        int scale = d.scale();
        String unscaled = d.unscaledValue().toString();
        int exponent = calculateExponent(d, precision, scale);

        StringBuilder s = new StringBuilder();
        if (negative) {
            s.append('-');
        }

        s.append(unscaled.substring(0, 1));
        String remaining = unscaled.substring(1);
        int extraZeroes = fractionDigits - remaining.length();
        if (!remaining.isEmpty() || extraZeroes > 0) {
            s.append('.');
            s.append(remaining);
            for (int i = 0; i < extraZeroes; i++) {
                s.append('0');
            }
        }
        s.append('e');
        if (exponent >= 0) {
            s.append('+');
        }
        s.append(exponent);

        return s.toString();
    }

    /**
     * Special handling for zero as shown in the spec for toExponential.
     */
    private static String exponentialZero(int fractionDigits) {
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

    private static int calculateExponent(BigDecimal d, int precision, int scale) {
        if (scale > 0) {
            if (precision <= scale) {
                // Smaller than zero, negative exponent
                return -(scale - precision + 1);
            }
            // Larger than zero, positive exponent
            return precision - scale + -1;
        }
        // Negative scale, positive exponent always
        return precision - scale - 1;
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
