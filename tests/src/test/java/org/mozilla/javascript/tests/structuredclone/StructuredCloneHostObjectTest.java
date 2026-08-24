/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.structuredclone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.StructuredCloneContext;
import org.mozilla.javascript.TopLevel;
import org.mozilla.javascript.VarScope;
import org.mozilla.javascript.annotations.JSStructuredCloneable;

/**
 * Tests that structured cloning is limited to standard types and host objects that explicitly opt
 * in via {@link JSStructuredCloneable} plus a {@code structuredClone} override.
 */
public class StructuredCloneHostObjectTest {

    @Test
    public void unannotatedHostObject_throwsDataCloneError() {
        EcmaError error =
                assertThrows(
                        EcmaError.class,
                        () ->
                                evaluate(
                                        "host",
                                        PlainHost.class,
                                        new PlainHost(),
                                        "structuredClone(host)"));
        assertTrue(error.getMessage().contains("not cloneable"), error.getMessage());
    }

    @Test
    public void plainObject_isCloned() throws Exception {
        Object result =
                evaluate(
                        null,
                        null,
                        null,
                        "var o = {a: 1, b: {c: 2}};"
                                + "var c = structuredClone(o);"
                                + "(c !== o) && (c.b !== o.b) && c.a === 1 && c.b.c === 2");
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void annotatedHostObject_withOverride_isCloned() throws Exception {
        CloneableHost host = new CloneableHost();
        host.count = 42;
        Object result =
                evaluate(
                        "host",
                        CloneableHost.class,
                        host,
                        "var c = structuredClone(host);" + "(c !== host) && c.count === 42");
        assertEquals(Boolean.TRUE, result);
    }

    private static Object evaluate(
            String hostName,
            Class<? extends ScriptableObject> hostClass,
            Object host,
            String script)
            throws Exception {
        Context cx = Context.enter();
        try {
            cx.setLanguageVersion(Context.VERSION_ES6);
            TopLevel top = cx.initStandardObjects();
            if (hostClass != null) {
                ScriptableObject hostObject = (ScriptableObject) host;
                ScriptableObject.defineClass(top, hostClass);
                hostObject.setPrototype(
                        ScriptableObject.getClassPrototype(top, hostObject.getClassName()));
                ScriptableObject.putProperty(top, hostName, host);
            }
            return cx.evaluateString(top, script, "test", 1, null);
        } finally {
            Context.exit();
        }
    }

    /** A host object without the annotation: must not be cloneable. */
    public static class PlainHost extends ScriptableObject {
        @Override
        public String getClassName() {
            return "PlainHost";
        }
    }

    /** A host object opting in: the annotation plus a {@code structuredClone} override. */
    @JSStructuredCloneable
    public static class CloneableHost extends ScriptableObject {
        int count;

        @Override
        public String getClassName() {
            return "CloneableHost";
        }

        public int jsGet_count() {
            return count;
        }

        @Override
        public Object structuredClone(Context cx, VarScope scope, StructuredCloneContext ccx) {
            CloneableHost target = new CloneableHost();
            target.count = count;
            target.setPrototype(ScriptableObject.getClassPrototype(scope, getClassName()));
            target.setParentScope(scope);
            ccx.remember(this, target);
            return target;
        }
    }
}
