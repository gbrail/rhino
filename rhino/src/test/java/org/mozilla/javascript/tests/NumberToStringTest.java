package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.dtoa.DoubleToDecimal;

public class NumberToStringTest {

    private static final Object[][] TO_STRING_TESTS = {
        // order: expected result, source
        {"0", 0.0},
        {"1", 1.0},
        {"-1", -1.0},
        {"0.000001", 0.000001},
        {"123.456", 123.456},
        {"-123.456", -123.456},
        {"1e+23", 1E23},
        {"1.0000000000000001e+23", 100000000000000000000001.0},
        {"3.14", 3.14},
        {"1000000000", 1E9},
        {"1e+31", 1E31},
        {"3.141592653589793", Math.PI},
        {"314159265358.9793", Math.PI * 100000000000.0},
        {"3.141592653589793e-11", Math.PI / 100000000000.0},
        {"3141592653589793000", Math.PI * 1000000000000000.0 * 1000.0},
        {"3.1415926535897934e-14", 3.1415926535897934E-14},
        {"3.141592653589793e+23", Math.PI * 1000000000000000.0 * 100000000.0},
        {"1e-7", 1E-7},
        {"1e+21", 1E21},
        // Denormals
        {"5.88e-39", 5.88E-39},
        {"4.47118444e-314", 4.47118444E-314}
    };

    private static Object[][] getToStringParams() {
        return TO_STRING_TESTS;
    }

    @ParameterizedTest
    @MethodSource("getToStringParams")
    public void testToString(String expected, double d) {
        String s = ScriptRuntime.toString(d);
        assertEquals(expected, s);
    }

    @ParameterizedTest
    @MethodSource("getToStringParams")
    public void testToStringDecimal(String expected, double v) {
        var d = DoubleToDecimal.toDecimal(v);
        System.out.println(
                "neg = " + d.negative() + " sig = " + d.significand() + " exp = " + d.exponent());
        System.out.println(d.toString());
        assertEquals(expected, d.toString());
    }

    private static final Object[][] CONVERT_TESTS = {
        // order: source, argument, to exponential, to fixed, to precision
        {0.0, 0, "0e+0", "0", ""},
        {0.0, 1, "0.0e+0", "0.0", "0"},
        {0.0, 2, "0.00e+0", "0.00", "0.0"},
        {
            0.0,
            100,
            "0.0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000e+0",
            "0.0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
            "0.000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
        },
        {1.0, 0, "1e+0", "1", ""},
        {1.0, 1, "1.0e+0", "1.0", "1"},
        {1.0, 10, "1.0000000000e+0", "1.0000000000", "1.000000000"},
        {0.000001, 1, "1.0e-6", "0.0", "0.000001"},
        {123.456, 0, "1e+2", "123", ""},
        {123.456, 1, "1.2e+2", "123.5", "1e+2"},
        {123.456, 2, "1.23e+2", "123.46", "1.2e+2"},
        {123.456, 10, "1.2345600000e+2", "123.4560000000", "123.4560000"},
        {-123.456, 10, "-1.2345600000e+2", "-123.4560000000", "-123.4560000"},
        {1E23, 4, "1.0000e+23", "1e+23", "1.000e+23"},
        {100000000000000000000001.0, 5, "1.00000e+23", "1.0000000000000001e+23", "1.0000e+23"},
        {Math.PI * 100000000000.0, 5, "3.14159e+11", "314159265358.97931", "3.1416e+11"},
        {
            Math.PI * 100000000000.0,
            29,
            "3.14159265358979309082031250000e+11",
            "314159265358.97930908203125000000000000000",
            "314159265358.97930908203125000"
        },
        {Math.PI / 100000000000000.0, 4, "3.1416e-14", "0.0000", "3.142e-14"},
        {5.88E-39, 1, "5.9e-39", "0.0", "6e-39"},
        {
            5.88E-39,
            72,
            "5.879999999999999682121697891273120514358604938932618538527697337758246910e-39",
            "0.000000000000000000000000000000000000005879999999999999682121697891273121",
            "5.87999999999999968212169789127312051435860493893261853852769733775824691e-39"
        },
    };

    private static Object[][] getConvertParams() {
        return CONVERT_TESTS;
    }

    /*
    @ParameterizedTest
    @MethodSource("getConvertParams")
    public void testToExponential(
            double v, int fractionDigits, String expected, String ignore1, String ignore2) {
        assertEquals(expected, BigDecimalDtoA.numberToStringExponential(v, fractionDigits));
    }

    @ParameterizedTest
    @MethodSource("getConvertParams")
    public void testToFixed(
            double v, int fractionDigits, String ignore1, String expected, String ignore2) {
        assertEquals(expected, BigDecimalDtoA.numberToStringFixed(v, fractionDigits));
    }

    @ParameterizedTest
    @MethodSource("getConvertParams")
    public void testToPrecision(
            double v, int precision, String ignore1, String ignore2, String expected) {
        if (precision >= 1) {
            assertEquals(expected, BigDecimalDtoA.numberToStringPrecision(v, precision));
        }
    }
        */
}
