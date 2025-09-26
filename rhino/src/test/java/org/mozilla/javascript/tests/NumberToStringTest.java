package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.MathContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mozilla.javascript.v8dtoa.BigDecimalDtoA;

public class NumberToStringTest {

    private static final Object[][] TESTS = {
        // order: expected result, source string
        {"0", 0.0},
        {"1", 1.0},
        {"-1", -1.0},
        {"123.456", 123.456},
        {"3.14", 3.14},
        {"1000000000", 1E9},
        {"1e+31", 1E31},
        {"3.141592653589793", Math.PI},
        {"314159265358.9793", Math.PI * 100000000000.0},
        {"3.141592653589793e-11", Math.PI / 100000000000.0},
        {"3141592653589793000", Math.PI * 1000000000000000.0 * 1000.0},
        {"3.141592653589793e+23", Math.PI * 1000000000000000.0 * 100000000.0},
        {"1e-7", 1E-7},
        {"1e+21", 1E21},
        // Denormals
        {"5.88e-39", 5.88E-39},
        {"4.47118444e-314", 4.47118444E-314}
    };

    private static Object[][] getParams() {
        return TESTS;
    }

    @ParameterizedTest
    @MethodSource("getParams")
    public void testBignumV8Conversions(String expected, double d) {
        String s = BigDecimalDtoA.dtoa(new BigDecimal(d, MathContext.DECIMAL64));
        assertEquals(expected, s);
    }
}
