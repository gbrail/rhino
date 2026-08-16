package org.mozilla.javascript.tests.structuredclone;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.*;

public class NegZeroDebugTest {
    @Test
    public void testSharedBuffer() {
        Context cx = Context.enter();
        try {
            cx.setLanguageVersion(Context.VERSION_ES6);
            VarScope scope = cx.initStandardObjects();
            NativeGlobal.init(cx, (TopLevel) scope, true);

            // All in one evaluateString to keep scope
            Object result =
                    cx.evaluateString(
                            scope,
                            "var buf = new ArrayBuffer(16);\n"
                                    + "var u8 = new Uint8Array(buf);\n"
                                    + "var i16 = new Int16Array(buf);\n"
                                    + "u8[0] = 1;\n"
                                    + "u8[1] = 2;\n"
                                    + "var origI16 = i16[0];\n"
                                    + "var copy = structuredClone({u8: u8, i16: i16});\n"
                                    + "var clonedI16 = copy.i16[0];\n"
                                    + "origI16 + ',' + clonedI16;",
                            "test",
                            1,
                            null);
            System.out.println("Result: " + result);
        } finally {
            Context.exit();
        }
    }
}
