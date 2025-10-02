package org.mozilla.javascript;

import org.mozilla.javascript.dtoa.BigDecimalToDecimal;
import org.mozilla.javascript.dtoa.Decimal;
import org.mozilla.javascript.dtoa.DoubleToDecimal;

public class NumberConversions {
    private static final int MAX_PRECISION = 100;
    private static final double MAX_FIXED = 1E21;

    /** The algorithm of Number.prototype.toExponential(fractionDigits). */
    public static String toExponential(double v, Object fracArgs) {
        double p = Undefined.isUndefined(fracArgs) ? 0.0 : ScriptRuntime.toInteger(fracArgs);
        if (!Double.isFinite(v)) {
            return ScriptRuntime.toString(v);
        }
        Decimal decimal;
        int fractionDigits;
        if (Undefined.isUndefined(fracArgs)) {
            // If undefined, use same precision as standard toString()
            decimal = DoubleToDecimal.toDecimal(v);
            fractionDigits = -1;
        } else {
            fractionDigits = checkPrecision(p, 0);
            // Arg is literally fraction digits, precision always one higher
            decimal = BigDecimalToDecimal.toPreciseDecimal(v, fractionDigits + 1);
        }
        return decimal.toExponentialString(fractionDigits);
    }

    /** The algorithm of Number.prototype.toFixed(fractionDigits). */
    public static String toFixed(Context cx, double v, Object fracArgs) {
        double p = Undefined.isUndefined(fracArgs) ? 0.0 : ScriptRuntime.toInteger(fracArgs);
        if (!Double.isFinite(p)) {
            throwBadPrecision(p);
        }
        int fracDigits = checkPrecision(p, cx.getLanguageVersion() < Context.VERSION_ES6 ? -20 : 0);
        if (!Double.isFinite(v) || v >= MAX_FIXED) {
            return ScriptRuntime.toString(v);
        }
        var decimal = BigDecimalToDecimal.toFixedDecimal(v, fracDigits);
        return decimal.toFixedString(fracDigits);
    }

    /** Implement the guts of Number.prototype.toPrecision(precision). */
    public static String toPrecision(double v, Object precisionArg) {
        if (Undefined.isUndefined(precisionArg) || !Double.isFinite(v)) {
            return ScriptRuntime.toString(v);
        }
        double p = ScriptRuntime.toInteger(precisionArg);
        if (!Double.isFinite(v)) {
            return ScriptRuntime.toString(v);
        }
        int precision = checkPrecision(p, 1);
        var d = BigDecimalToDecimal.toPreciseDecimal(v, precision);
        if (d.exponent() < -6 || d.exponent() > precision) {
            return d.toExponentialString(precision - 1);
        } else {
            return d.toFixedPrecisionString(precision);
        }
    }

    private static int checkPrecision(double p, int precisionMin) {
        /*
         * Older releases allowed a larger range of precision than
         * ECMA requires.
         */
        if (p < precisionMin || p > MAX_PRECISION) {
            throwBadPrecision(p);
        }
        return ScriptRuntime.toInt32(p);
    }

    private static void throwBadPrecision(double p) {
        String msg = ScriptRuntime.getMessageById("msg.bad.precision", ScriptRuntime.toString(p));
        throw ScriptRuntime.rangeError(msg);
    }
}
