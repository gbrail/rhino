package org.mozilla.javascript;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalDtoA {

    private static final double TEN_TO_21 = Math.pow(10.0, 21.0);

    /**
     * Implement Number::toString as specified in ECMAScript under "abstract
     * operations." Currently
     * only implemented for radix 10.
     */
    public static String numberToString(double v) {
        if (!Double.isFinite(v)) {
            return ScriptRuntime.toString(v);
        }
        // ECMAScript section 6 says that numbers correspond to the "binary64"
        // standard, and that the "round ties to even" mode should be used.
        BigDecimal d = new BigDecimal(v, MathContext.DECIMAL64);
        // This function (and not the others) asks for the shortest possible
        // representation.
        d = d.stripTrailingZeros();

        // Save negativity for later to make formatting easier
        StringBuilder s = new StringBuilder();
        if (d.signum() < 0) {
            d = d.negate();
            s.append('-');
        }

        int precision = d.precision();
        int scale = d.scale();
        // Subsequent operations are string manipulations on this
        String unscaled = d.unscaledValue().toString();
        int exponent = calculateExponent(d, precision, scale);

        // System.out.println(v + " u = " + unscaled + " p = " + precision + " s = " +
        // scale + " e = " + exponent);

        if ((exponent >= -6) && (exponent < 21)) {
            // Straight decimal notation
            if (scale < 0) {
                // Very big number with no decimal point
                assert exponent > 0;
                s.append(unscaled);
                addZeroes(s, -scale);
            } else if (precision <= scale) {
                // Very small number less than zero
                assert exponent < 0;
                s.append("0.");
                addZeroes(s, scale - precision);
                s.append(unscaled);
            } else {
                // A fairly normal number
                assert precision > scale;
                s.append(unscaled.substring(0, precision - scale));
                String remaining = unscaled.substring(precision - scale);
                if (!remaining.isEmpty()) {
                    s.append('.');
                    s.append(remaining);
                }
            }
        } else {
            // Exponential notation
            s.append(unscaled.substring(0, 1));
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
        StringBuilder s = new StringBuilder();
        if (d.signum() < 0) {
            d = d.negate();
            s.append('-');
        }

        int scale = fractionDigits;
        // This can round and reduce precision, or increase scale and add zeroes.
        d = d.setScale(scale, RoundingMode.HALF_UP);
        int precision = d.precision();
        String unscaled = d.unscaledValue().toString();
        int exponent = calculateExponent(d, precision, scale);

        if (scale < 0) {
            // Very big number with no decimal point
            assert exponent > 0;
            s.append(unscaled);
            addZeroes(s, -scale);
        } else if (precision <= scale) {
            // Very small number less than zero
            assert exponent < 0;
            s.append("0.");
            addZeroes(s, scale - precision);
            s.append(unscaled);
        } else {
            // A fairly normal number
            assert precision > scale;
            s.append(unscaled.substring(0, precision - scale));
            String remaining = unscaled.substring(precision - scale);
            if (!remaining.isEmpty()) {
                s.append('.');
                s.append(remaining);
            }
        }

        return s.toString();
    }

    /**
     * Implement Number.prototype.toExponential as specified in ECMAScript for the
     * Number prototype.
     * If fraction digits was undefined then pass -1.
     */
    public static String numberToStringExponential(double v, int fractionDigits) {
        if (!Double.isFinite(v) || fractionDigits < 0) {
            return ScriptRuntime.toString(v);
        }
        // This is one of the functions that expects to work with
        // more precision than toString()
        BigDecimal d = new BigDecimal(v, MathContext.UNLIMITED);
        int sig = d.signum();
        if (sig == 0) {
            return exponentialZero(fractionDigits);
        } else if (sig < 0) {
            d = d.negate();
        }

        // Reduce precision if necessary. Test262 tests round "half up" which
        // is different from the rounding mode used in toString()
        d = d.round(new MathContext(fractionDigits + 1, RoundingMode.HALF_UP));

        int precision = d.precision();
        int scale = d.scale();
        String unscaled = d.unscaledValue().toString();
        int exponent = calculateExponent(d, precision, scale);

        StringBuilder s = new StringBuilder();
        if (sig < 0) {
            s.append('-');
        }

        s.append(unscaled.substring(0, 1));
        String remaining = unscaled.substring(1);
        int extraZeroes = fractionDigits - remaining.length();
        if (!remaining.isEmpty() || extraZeroes > 0) {
            s.append('.');
            s.append(remaining);
            addZeroes(s, extraZeroes);
        }
        s.append('e');
        if (exponent >= 0) {
            s.append('+');
        }
        s.append(exponent);

        return s.toString();
    }

    public static String numberToStringPrecision(double v, int desiredPrecision) {
        if (!Double.isFinite(v)) {
            return ScriptRuntime.toString(v);
        }
        // This is one of the functions that expects to work with
        // more precision than toString()
        BigDecimal d = new BigDecimal(v, MathContext.UNLIMITED);
        StringBuilder s = new StringBuilder();
        int sig = d.signum();
        if (sig == 0) {
            return precisionZero(desiredPrecision);
        } else if (sig < 0) {
            d = d.negate();
            s.append('-');
        }

        d = d.round(new MathContext(desiredPrecision, RoundingMode.HALF_UP));

        int precision = d.precision();
        int scale = d.scale();
        // Subsequent operations are string manipulations on this
        String unscaled = d.unscaledValue().toString();
        int exponent = calculateExponent(d, precision, scale);

        if (exponent >= -6 && exponent < desiredPrecision) {
            // Straight decimal notation
            if (scale < 0) {
                // Very big number with no decimal point
                assert exponent > 0;
                s.append(unscaled);
                addZeroes(s, -scale);
            } else if (precision <= scale) {
                // Very small number less than zero
                assert exponent < 0;
                s.append("0.");
                addZeroes(s, scale - precision);
                s.append(unscaled);
            } else {
                // A fairly normal number
                assert precision > scale;
                s.append(unscaled.substring(0, precision - scale));
                String remaining = unscaled.substring(precision - scale);
                int extraZeroes = desiredPrecision - unscaled.length();
                if (!remaining.isEmpty() || extraZeroes > 0) {
                    s.append('.');
                    s.append(remaining);
                    addZeroes(s, extraZeroes);
                }
            }
        } else {
            // Exponential notation
            s.append(unscaled.substring(0, 1));
            String remaining = unscaled.substring(1);
            int extraZeroes = desiredPrecision - unscaled.length();
            if (!remaining.isEmpty() || extraZeroes > 0) {
                s.append('.');
                s.append(remaining);
                addZeroes(s, extraZeroes);
            }
            s.append('e');
            if (exponent > 0) {
                s.append('+');
            }
            s.append(exponent);
        }
        return s.toString();
    }

    /** Special handling for zero as shown in the spec. */
    private static String exponentialZero(int fractionDigits) {
        StringBuilder s = new StringBuilder();
        s.append('0');
        if (fractionDigits > 0) {
            s.append('.');
        }
        addZeroes(s, fractionDigits);
        s.append("e+0");
        return s.toString();
    }

    private static String precisionZero(int precision) {
        StringBuilder s = new StringBuilder();
        s.append('0');
        if (precision > 1) {
            s.append('.');
        }
        addZeroes(s, precision - 1);
        return s.toString();
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

    private static void addZeroes(StringBuilder b, int n) {
        for (int i = 0; i < n; i++) {
            b.append('0');
        }
    }
}
