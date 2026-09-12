/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.es6;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.testutils.Utils;

public class GeneratorCompletionTest {

    @Test
    public void completionCarriesReturnValue() {
        String code =
                "function* g() { yield 1; return 2; }\n"
                        + "var it = g();\n"
                        + "var a = it.next();\n"
                        + "var b = it.next();\n"
                        + "var c = it.next();\n"
                        + "JSON.stringify([a.value, a.done, b.value, b.done, c.value, c.done]);";
        Utils.assertWithAllModes_ES6("[1,false,2,true,null,true]", code);
    }

    @Test
    public void immediateReturnCompletesGenerator() {
        String code =
                "function* g() { return 42; }\n"
                        + "var r = g().next();\n"
                        + "JSON.stringify([r.value, r.done]);";
        Utils.assertWithAllModes_ES6("[42,true]", code);
    }

    @Test
    public void naturalEndCompletesWithUndefined() {
        String code =
                "function* g() { yield 1; }\n"
                        + "var it = g();\n"
                        + "it.next();\n"
                        + "var r = it.next();\n"
                        + "JSON.stringify([r.value === undefined, r.done]);";
        Utils.assertWithAllModes_ES6("[true,true]", code);
    }

    @Test
    public void finallyRunsBeforeCompletion() {
        String code =
                "var ran = false;\n"
                        + "function* g() {\n"
                        + "  try {\n"
                        + "    yield 1;\n"
                        + "    return 2;\n"
                        + "  } finally {\n"
                        + "    ran = true;\n"
                        + "  }\n"
                        + "}\n"
                        + "var it = g();\n"
                        + "it.next();\n"
                        + "var r = it.next();\n"
                        + "JSON.stringify([r.value, r.done, ran]);";
        Utils.assertWithAllModes_ES6("[2,true,true]", code);
    }

    @Test
    public void thrownValueStillPropagates() {
        String code =
                "function* g() { yield 1; throw 'err'; }\n"
                        + "var it = g();\n"
                        + "it.next();\n"
                        + "var res = '';\n"
                        + "try { it.next(); } catch (e) { res = String(e); }\n"
                        + "res;";
        Utils.assertWithAllModes_ES6("err", code);
    }

    @Test
    public void explicitReturnCompletesGenerator() {
        String code =
                "function* g() { yield 1; yield 2; }\n"
                        + "var it = g();\n"
                        + "it.next();\n"
                        + "var r = it.return(9);\n"
                        + "JSON.stringify([r.value, r.done]);";
        Utils.assertWithAllModes_ES6("[9,true]", code);
    }

    @Test
    public void yieldStarCompletionContinuesOuterGenerator() {
        String code =
                "function* inner() { yield 1; return 2; }\n"
                        + "function* outer() { yield* inner(); yield 3; }\n"
                        + "var it = outer();\n"
                        + "var a = it.next();\n"
                        + "var b = it.next();\n"
                        + "var c = it.next();\n"
                        + "JSON.stringify([a.value, a.done, b.value, b.done, c.value, c.done]);";
        Utils.assertWithAllModes_ES6("[1,false,3,false,null,true]", code);
    }
}
