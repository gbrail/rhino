package org.mozilla.javascript.dtoa;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalToDecimal {
    public static Decimal toStandardDecimal(double d) {
        assert Double.isFinite(d);
        return toDecimal(new BigDecimal(d, MathContext.DECIMAL64));
    }

    public static Decimal toFixedDecimal(double d, int fractionDigits) {
        assert Double.isFinite(d);
        BigDecimal bd =
                new BigDecimal(d, MathContext.UNLIMITED)
                        .setScale(fractionDigits, RoundingMode.HALF_EVEN);
        return toDecimal(bd);
    }

    public static Decimal toPreciseDecimal(double d, int precision) {
        assert Double.isFinite(d);
        BigDecimal bd = new BigDecimal(d, new MathContext(precision, RoundingMode.HALF_UP));
        return toDecimal(bd);
    }

    private static Decimal toDecimal(BigDecimal bd) {
        boolean negative;

        if (bd.signum() < 0) {
            negative = true;
            bd = bd.negate();
        } else {
            negative = false;
        }

        int scale = bd.scale();
        int precision = bd.precision();
        int exponent;

        if (scale > 0) {
            if (precision <= scale) {
                // Smaller than zero, negative exponent
                exponent = -(scale - precision);
            } else {
                // Larger than zero, positive exponent
                exponent = precision - scale;
            }
        } else {
            // Negative scale, positive exponent always
            exponent = precision - scale;
        }
        return new Decimal(bd, exponent, negative);
    }
}
