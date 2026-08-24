/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.structuredclone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.StructuredClone;
import org.mozilla.javascript.TopLevel;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.typedarrays.NativeArrayBuffer;

/**
 * Tests {@link StructuredClone#cloneIntoScope}, the Java-only entry point that clones a graph and
 * wires every object into a different top-level scope.
 */
public class StructuredCloneIntoScopeTest {

    @Test
    public void graphIsClonedAndWiredToTargetRealm() {
        try (var cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            TopLevel realmA = cx.initStandardObjects();
            TopLevel realmB = cx.initStandardObjects();

            Object source =
                    cx.evaluateString(
                            realmA,
                            "var o = {nested: {v: 's'}, arr: [1, 2, 3],"
                                    + "map: new Map([['k', {deep: true}]]), set: new Set([1, 2]),"
                                    + "date: new Date(1234567890), re: /ab+c/gi,"
                                    + "err: new TypeError('boom'), buf: new Int32Array([7, 8])};"
                                    + "o.self = o;"
                                    + "o;",
                            "test",
                            1,
                            null);

            ScriptableObject clone =
                    (ScriptableObject) StructuredClone.cloneIntoScope(cx, realmB, source);

            assertNotSame(source, clone);
            assertEquals(realmB, clone.getParentScope());
            assertEquals(
                    ScriptableObject.getClassPrototype(realmB, "Object"), clone.getPrototype());

            realmB.put("c", realmB, clone);
            assertEquals(
                    Boolean.TRUE,
                    cx.evaluateString(
                            realmB,
                            "(function () {"
                                    + "if (!(c instanceof Object)) return false;"
                                    + "if (Object.getPrototypeOf(c) !== Object.prototype)"
                                    + "return false;"
                                    + "if (!(c.arr instanceof Array)) return false;"
                                    + "if (!(c.map instanceof Map)) return false;"
                                    + "if (!(c.set instanceof Set)) return false;"
                                    + "if (!(c.date instanceof Date)) return false;"
                                    + "if (!(c.re instanceof RegExp)) return false;"
                                    + "if (!(c.err instanceof TypeError)) return false;"
                                    + "if (!(c.buf instanceof Int32Array)) return false;"
                                    + "if (c.self !== c) return false;"
                                    + "if (c.nested.v !== 's') return false;"
                                    + "if (c.arr.length !== 3 || c.arr[0] !== 1) return false;"
                                    + "if (!c.map.has('k') || !c.map.get('k').deep) return false;"
                                    + "if (!c.set.has(2)) return false;"
                                    + "if (c.date.getTime() !== 1234567890) return false;"
                                    + "if (String(c.re) !== '/ab+c/gi') return false;"
                                    + "if (c.err.message !== 'boom') return false;"
                                    + "if (c.buf[0] !== 7 || c.buf[1] !== 8) return false;"
                                    + "return true;"
                                    + "})()",
                            "test",
                            1,
                            null));

            realmA.put("c", realmA, clone);
            assertEquals(
                    Boolean.TRUE,
                    cx.evaluateString(realmA, "!(c.arr instanceof Array)", "test", 1, null));
        }
    }

    @Test
    public void nestedObjectsAreWiredToTargetRealm() {
        try (var cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            TopLevel realmA = cx.initStandardObjects();
            TopLevel realmB = cx.initStandardObjects();

            Object source =
                    cx.evaluateString(
                            realmA,
                            "({arr: [{inner: new Map()}], ab: new ArrayBuffer(8)})",
                            "test",
                            1,
                            null);

            ScriptableObject clone =
                    (ScriptableObject) StructuredClone.cloneIntoScope(cx, realmB, source);

            ScriptableObject arr = (ScriptableObject) ScriptableObject.getProperty(clone, "arr");
            assertEquals(realmB, arr.getParentScope());
            assertEquals(ScriptableObject.getClassPrototype(realmB, "Array"), arr.getPrototype());

            ScriptableObject element = (ScriptableObject) ScriptableObject.getProperty(arr, 0);
            assertEquals(realmB, element.getParentScope());

            ScriptableObject innerMap =
                    (ScriptableObject) ScriptableObject.getProperty(element, "inner");
            assertEquals(realmB, innerMap.getParentScope());
            assertEquals(
                    ScriptableObject.getClassPrototype(realmB, "Map"), innerMap.getPrototype());

            ScriptableObject buffer = (ScriptableObject) ScriptableObject.getProperty(clone, "ab");
            assertEquals(realmB, buffer.getParentScope());
            assertEquals(
                    ScriptableObject.getClassPrototype(realmB, "ArrayBuffer"),
                    buffer.getPrototype());
        }
    }

    @Test
    public void isolateScopeIsSupportedAsTarget() {
        try (var cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            TopLevel realmA = cx.initStandardObjects();
            TopLevel isolate = TopLevel.createIsolate(realmA);

            Object source = cx.evaluateString(realmA, "({a: [1]})", "test", 1, null);
            ScriptableObject clone =
                    (ScriptableObject) StructuredClone.cloneIntoScope(cx, isolate, source);

            assertEquals(isolate, clone.getParentScope());
            // The isolate delegates built-ins to the shared realm.
            assertEquals(
                    ScriptableObject.getClassPrototype(realmA, "Object"), clone.getPrototype());
        }
    }

    @Test
    public void transferredBufferIsMovedToTargetRealm() {
        try (var cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            TopLevel realmA = cx.initStandardObjects();
            TopLevel realmB = cx.initStandardObjects();

            Object source =
                    cx.evaluateString(
                            realmA,
                            "var ab = new ArrayBuffer(8);"
                                    + "var v1 = new Int32Array(ab);"
                                    + "var v2 = new Int32Array(ab, 4);"
                                    + "v1[0] = 0x11223344;"
                                    + "v2[0] = 0x55667788;"
                                    + "({ab: ab, v1: v1, v2: v2});",
                            "test",
                            1,
                            null);
            NativeArrayBuffer buffer =
                    (NativeArrayBuffer)
                            ScriptableObject.getProperty((ScriptableObject) source, "ab");

            ScriptableObject clone =
                    (ScriptableObject)
                            StructuredClone.cloneIntoScope(cx, realmB, source, List.of(buffer));

            ScriptableObject clonedBuffer =
                    (ScriptableObject) ScriptableObject.getProperty(clone, "ab");
            assertNotSame(buffer, clonedBuffer);
            assertEquals(realmB, clonedBuffer.getParentScope());

            realmB.put("c", realmB, clone);
            assertEquals(
                    Boolean.TRUE,
                    cx.evaluateString(
                            realmB,
                            "(function () {"
                                    + "if (!(c.ab instanceof ArrayBuffer)) return false;"
                                    + "if (c.ab.byteLength !== 8) return false;"
                                    + "if (!(c.v1 instanceof Int32Array)) return false;"
                                    + "if (c.v1[0] !== 0x11223344) return false;"
                                    + "if (c.v2[0] !== 0x55667788) return false;"
                                    + "if (c.v1.buffer !== c.ab || c.v2.buffer !== c.ab)"
                                    + "return false;"
                                    + "return true;"
                                    + "})()",
                            "test",
                            1,
                            null));

            // The source buffer is detached and its views report zero length.
            assertEquals(
                    Boolean.TRUE,
                    cx.evaluateString(
                            realmA,
                            "ab.byteLength === 0 && v1.byteLength === 0 && v2.byteLength === 0",
                            "test",
                            1,
                            null));
        }
    }

    @Test
    public void unlistedBufferIsCopied() {
        try (var cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            TopLevel realmA = cx.initStandardObjects();
            TopLevel realmB = cx.initStandardObjects();

            Object source =
                    cx.evaluateString(
                            realmA,
                            "var ab = new ArrayBuffer(4);"
                                    + "new Int32Array(ab)[0] = 42;"
                                    + "({ab: ab});",
                            "test",
                            1,
                            null);

            ScriptableObject clone =
                    (ScriptableObject) StructuredClone.cloneIntoScope(cx, realmB, source);

            ScriptableObject clonedBuffer =
                    (ScriptableObject) ScriptableObject.getProperty(clone, "ab");
            ScriptableObject sourceBuffer =
                    (ScriptableObject)
                            ScriptableObject.getProperty((ScriptableObject) source, "ab");
            assertNotSame(sourceBuffer, clonedBuffer);

            realmB.put("c", realmB, clone);
            assertEquals(
                    Boolean.TRUE,
                    cx.evaluateString(realmB, "new Int32Array(c.ab)[0] === 42", "test", 1, null));
            assertEquals(
                    Boolean.TRUE,
                    cx.evaluateString(
                            realmA,
                            "ab.byteLength === 4 && new Int32Array(ab)[0] === 42",
                            "test",
                            1,
                            null));
        }
    }

    @Test
    public void primitivesPassThroughUnchanged() {
        try (var cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            TopLevel realmA = cx.initStandardObjects();
            TopLevel realmB = cx.initStandardObjects();
            for (Object value : new Object[] {null, Undefined.instance, true, 42, "hello"}) {
                assertEquals(value, StructuredClone.cloneIntoScope(cx, realmB, value));
            }
        }
    }

    @Test
    public void functionThrowsDataCloneError() {
        try (var cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            TopLevel realmA = cx.initStandardObjects();
            TopLevel realmB = cx.initStandardObjects();
            Object fn = cx.evaluateString(realmA, "(function () {})", "test", 1, null);

            EcmaError error =
                    assertThrows(
                            EcmaError.class, () -> StructuredClone.cloneIntoScope(cx, realmB, fn));
            assertTrue(
                    error.getMessage().contains("Functions are not cloneable"), error.getMessage());
        }
    }
}
