package org.mozilla.javascript.typedarrays;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Comprehensive tests for Float16 conversion logic in Conversions class. */
public class ConversionsTest {

    private static final double EPSILON = 1e-10;

    private float roundTrip(double value) {
        short bits = Conversions.float16ToShortBits((float) value);
        return Conversions.shortBitsToFloat16(bits);
    }

    // === Zero Tests ===

    @Test
    public void testPositiveZero() {
        float result = roundTrip(0.0);
        assertEquals(0.0f, result, 0.0f);
        assertTrue(Float.floatToIntBits(result) == Float.floatToIntBits(0.0f));
        assertEquals(0, Conversions.float16ToShortBits(0.0f));
    }

    @Test
    public void testNegativeZero() {
        float result = roundTrip(-0.0);
        assertEquals(-0.0f, result, 0.0f);
        assertTrue(Float.floatToIntBits(result) == Float.floatToIntBits(-0.0f));
        assertEquals((short) 0x8000, Conversions.float16ToShortBits(-0.0f));
    }

    // === Simple Value Tests ===

    @Test
    public void testOne() {
        float result = roundTrip(1.0);
        assertEquals(1.0f, result, EPSILON);
    }

    @Test
    public void testMinusOne() {
        float result = roundTrip(-1.0);
        assertEquals(-1.0f, result, EPSILON);
    }

    @Test
    public void testTwo() {
        float result = roundTrip(2.0);
        assertEquals(2.0f, result, EPSILON);
        assertEquals((short) 0x4000, Conversions.float16ToShortBits(2.0f));
    }

    @Test
    public void testOneHalf() {
        float result = roundTrip(0.5);
        assertEquals(0.5f, result, EPSILON);
    }

    // === Infinity Tests ===

    @Test
    public void testPositiveInfinity() {
        float result = roundTrip(Float.POSITIVE_INFINITY);
        assertTrue(Float.isInfinite(result));
        assertTrue(result > 0);
        assertEquals((short) 0x7C00, Conversions.float16ToShortBits(Float.POSITIVE_INFINITY));
    }

    @Test
    public void testNegativeInfinity() {
        float result = roundTrip(Float.NEGATIVE_INFINITY);
        assertTrue(Float.isInfinite(result));
        assertTrue(result < 0);
        assertEquals((short) 0xFC00, Conversions.float16ToShortBits(Float.NEGATIVE_INFINITY));
    }

    // === NaN Tests ===

    @Test
    public void testNaN() {
        float result = roundTrip(Float.NaN);
        assertTrue(Float.isNaN(result));
        short bits = Conversions.float16ToShortBits(Float.NaN);
        int exponent = (bits & 0xffff) >>> 10 & 0x1f;
        int mantissa = bits & 0x3ff;
        assertEquals(31, exponent);
        assertTrue(mantissa != 0);
    }

    // === Overflow Tests ===

    @Test
    public void testMaxValue() {
        float result = roundTrip(65504.0);
        assertEquals(65504.0f, result, 0.0f);
        assertEquals((short) 0x7BFF, Conversions.float16ToShortBits(65504.0f));
    }

    @Test
    public void testOverflowToPositiveInfinity() {
        float result = roundTrip(100000.0);
        assertEquals(Float.POSITIVE_INFINITY, result, 0.0f);
    }

    @Test
    public void testOverflowToNegativeInfinity() {
        float result = roundTrip(-100000.0);
        assertEquals(Float.NEGATIVE_INFINITY, result, 0.0f);
    }

    // === Denormalized Number Tests ===

    @Test
    public void testMinPositiveNormal() {
        double minNormal = Math.pow(2, -14);
        float result = roundTrip(minNormal);
        assertEquals((float) minNormal, result, 1e-7);
    }

    @Test
    public void testDenormalizedNumber() {
        double denormal = Math.pow(2, -20);
        float result = roundTrip(denormal);
        assertEquals((float) denormal, result, (float) denormal * 0.1f);
    }

    @Test
    public void testMinPositiveSubnormal() {
        double minSubnormal = Math.pow(2, -24);
        float result = roundTrip(minSubnormal);
        assertTrue(result > 0);
        assertTrue(result < Math.pow(2, -14));
    }

    @Test
    public void testUnderflowToZero() {
        double tinyValue = Math.pow(2, -25);
        float result = roundTrip(tinyValue);
        assertEquals(0.0f, result, 0.0f);
    }

    // === Precision Tests ===

    @Test
    public void testPi() {
        float result = roundTrip(Math.PI);
        assertEquals((float) Math.PI, result, 0.001f);
    }

    @Test
    public void testE() {
        float result = roundTrip(Math.E);
        assertEquals((float) Math.E, result, 0.001f);
    }

    // === Rounding Tests ===

    @Test
    public void testRoundingHalfToEven() {
        assertEquals(1.5f, roundTrip(1.5), EPSILON);
        assertEquals(2.5f, roundTrip(2.5), EPSILON);
    }

    @Test
    public void testSequentialValues() {
        double[] values = {
            -1000.0, -100.0, -10.0, -1.0, -0.1, -0.01, 0.0, 0.01, 0.1, 1.0, 10.0, 100.0, 1000.0
        };

        for (double value : values) {
            float result = roundTrip(value);
            if (Math.abs(value) <= 65504) {
                assertFalse(Float.isInfinite(result), "Value " + value + " should not overflow");
            }
        }
    }

    @Test
    public void testPowersOfTwo() {
        for (int exp = -14; exp <= 15; exp++) {
            double value = Math.pow(2, exp);
            float result = roundTrip(value);
            assertEquals((float) value, result, (float) value * 1e-6f);
        }
    }

    @Test
    public void testDenormalizedRoundingPrecision() {
        // This value would round up if using double precision but should round down when cast to
        // float first.
        double val = 2.980232238769532e-8;
        short bits = Conversions.float16ToShortBits((float) val);
        float result = Conversions.shortBitsToFloat16(bits);
        assertEquals(
                0.0f, result, 0.0f, "Value " + val + " should convert to 0 via Float32 precision");
    }
}
