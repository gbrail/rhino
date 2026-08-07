/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.typedarrays;

import org.mozilla.javascript.ScriptRuntime;

/** Numeric conversions from section 7 of the ECMAScript 6 standard. */
public class Conversions {
    // Float16 constants
    private static final double FLOAT16_MIN_NORMAL = 6.103515625e-5; // 2^-14
    private static final double FLOAT16_MIN_SUBNORMAL = 5.960464477539063E-8; // 2^-24

    public static int toInt8(Object arg) {
        return (byte) ScriptRuntime.toInt32(arg);
    }

    public static int toUint8(Object arg) {
        return ScriptRuntime.toInt32(arg) & 0xff;
    }

    public static int toUint8Clamp(Object arg) {
        double d = ScriptRuntime.toNumber(arg);
        if (d <= 0.0) {
            return 0;
        }
        if (d >= 255.0) {
            return 255;
        }

        // Complex rounding behavior -- see 7.1.11
        double f = Math.floor(d);
        if ((f + 0.5) < d) {
            return (int) (f + 1.0);
        }
        if (d < (f + 0.5)) {
            return (int) f;
        }
        if (((int) f % 2) != 0) {
            return (int) f + 1;
        }
        return (int) f;
    }

    public static short toInt16(Object arg) {
        return (short) ScriptRuntime.toInt32(arg);
    }

    public static short toUint16(Object arg) {
        return (short) (ScriptRuntime.toInt32(arg) & 0xffff);
    }

    public static int toInt32(Object arg) {
        return ScriptRuntime.toInt32(arg);
    }

    public static int toUint32(Object arg) {
        return (int) ScriptRuntime.toUint32(arg);
    }

    public static int byteBitsToUint(int bits) {
        return bits & 0xff;
    }

    public static int shortBitsToUint(short bits) {
        return bits & 0xffff;
    }

    public static long intBitsToUint(int bits) {
        return bits & 0xffffffffL;
    }

    public static float shortBitsToFloat16(short sb) {
        int bits = sb & 0xffff;

        // Extract sign, exponent, and mantissa
        int sign = (bits >>> 15) & 0x1;
        int exponent = (bits >>> 10) & 0x1f;
        int mantissa = bits & 0x3ff;

        // Handle special cases
        if (exponent == 0) {
            if (mantissa == 0) {
                // Zero
                return sign == 0 ? 0.0f : -0.0f;
            }

            // Denormalized number
            float value = (float) ((double) mantissa / (1 << 10) * FLOAT16_MIN_NORMAL);
            return sign == 0 ? value : -value;

        } else if (exponent == 31) {
            if (mantissa == 0) {
                // Infinity
                return sign == 0 ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
            }

            // NaN
            return Float.NaN;

        } else {
            // Normalized number
            float value =
                    (float) ((1.0 + (double) mantissa / (1 << 10)) * Math.pow(2, exponent - 15));
            return sign == 0 ? value : -value;
        }
    }

    public static short float16ToShortBits(double val) {
        float fval = (float) val;

        // Handle special cases
        if (Float.isNaN(fval)) {
            return 0x7e00;
        }

        int sign = (Float.floatToIntBits(fval) >>> 31) & 0x1;
        float absVal = Math.abs(fval);

        if (Float.isInfinite(fval)) {
            return (short) ((sign << 15) | 0x7c00);
        }

        if (absVal == 0.0f) {
            // Zero
            return (short) (sign << 15);
        }

        // Convert to float16
        int exponent;
        int mantissa;

        // Check overflow using original double precision
        // Max representable float16 is 65504 (exp=30, mantissa=0x3ff)
        // Values >= 65520 definitely overflow to infinity
        // Values in [65504, 65520) might round to 65504 or infinity
        double absValDouble = Math.abs(val);
        if (absValDouble >= 65520.0) {
            // Definite overflow to infinity
            return (short) ((sign << 15) | 0x7c00);
        } else if (absValDouble > 65504.0) {
            // Near overflow: value is between max finite and definite overflow
            // These values might round to 65504 or infinity depending on exact value
            // 65504 = 0x7BFF: exp=30, mantissa=0x3ff (all 1s)
            // Halfway point to next value would cause overflow
            // Values < 65520 should round to 65504
            return (short) ((sign << 15) | 0x7BFF);
        }
        if (absVal < FLOAT16_MIN_NORMAL) {
            // Denormalized number - IEEE 754 round-to-nearest-even
            // Use original double precision for accuracy in denormalized range
            exponent = 0;

            double ratio = Math.abs(val) / FLOAT16_MIN_SUBNORMAL;
            int mantissaBase = (int) ratio;
            double fractional = ratio - mantissaBase;

            // IEEE 754 round-to-nearest-even
            if (fractional > 0.5) {
                // More than halfway: round up
                mantissa = mantissaBase + 1;
            } else if (fractional == 0.5) {
                // Tie: round to even
                if ((mantissaBase & 1) != 0) {
                    mantissa = mantissaBase + 1; // Round up if odd
                } else {
                    mantissa = mantissaBase; // Keep if even
                }
            } else {
                // Less than halfway: round down
                mantissa = mantissaBase;
            }
        } else {
            // Normalized
            int exp32 = ((Float.floatToIntBits(fval) >>> 23) & 0xff) - 127;
            exponent = exp32 + 15;

            // DEFENSIVE CHECK (UNCOVERED): Exponent overflow to infinity
            // This check is unreachable through normal API usage because:
            // - Guard at line 239 catches all values >= 65520 (which have exp32 >= 16)
            // - For exponent >= 31: need exp32 >= 16, meaning Float32 >= 2^16 = 65536
            // - But 65536 >= 65520, so caught by guard before reaching here
            // - Mathematical impossibility: Can't pass guard (< 65520) AND overflow (>= 65536)
            // This defensive check is kept for defense-in-depth in case guards are modified.
            // See UNCOVERED_CODE_EXPLANATION.md for full mathematical proof.
            if (exponent >= 31) {
                // Overflow to infinity
                return (short) ((sign << 15) | 0x7c00);
            }

            // Normalized mantissa processing
            // Note: exponent > 0 is guaranteed here because:
            // - We're in the normalized path (absVal >= FLOAT16_MIN_NORMAL = 2^-14)
            // - Therefore exp32 >= -14, so exponent = exp32 + 15 >= 1
            int mant32 = Float.floatToIntBits(fval) & 0x7fffff;

            // Implement IEEE 754 round-to-nearest-even
            int bitsToShift = mant32 & 0x1fff; // Bits [12:0] that will be lost
            mantissa = mant32 >>> 13; // Bits [22:13] become the new mantissa

            if (bitsToShift > 0x1000) {
                // More than halfway: round up
                mantissa++;
            } else if (bitsToShift == 0x1000) {
                // Exactly halfway: round to even (check LSB)
                if ((mantissa & 1) != 0) {
                    mantissa++; // Round up if odd
                }
            }
            // else: Less than halfway, round down (mantissa already truncated)

            // Handle rounding overflow
            if (mantissa >= 0x400) {
                exponent++;
                mantissa = 0;
                // DEFENSIVE CHECK (UNCOVERED): Mantissa rounding causes exponent overflow
                // This check is unreachable through normal API usage because:
                // - For this to execute: need exponent=30 initially, then mantissa rounds to 0x400
                // - This requires Float32 values near 2^16 (approximately 65504-65536 range)
                // - Guard at line 243 catches all values > 65504
                // - Guard at line 239 catches all values >= 65520
                // - Complete coverage: All overflow cases caught by guards before reaching here
                // This defensive check is kept for defense-in-depth in case guards are modified.
                // See UNCOVERED_CODE_EXPLANATION.md for full mathematical proof.
                if (exponent >= 31) {
                    // Overflow to infinity
                    return (short) ((sign << 15) | 0x7c00);
                }
            }
        }

        return (short) ((sign << 15) | (exponent << 10) | mantissa);
    }
}
