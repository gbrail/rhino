package org.mozilla.javascript.tests;

import static org.junit.Assert.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.optimizer.Bootstrapper;

public class DynamicOperationsTest {
    private Context cx;
    private Scriptable scope;
    private Scriptable smallObject;

    @Before
    public void init() {
        cx = Context.enter();
        cx.setOptimizationLevel(9);
        cx.setLanguageVersion(Context.VERSION_ES6);
        scope = cx.initStandardObjects();
        smallObject = cx.newObject(scope);
        smallObject.put("one", smallObject, 1);
        smallObject.put("two", smallObject, 2);
        smallObject.put("three", smallObject, 3);
    }

    @After
    public void close() {
        Context.exit();
    }

    @Test
    public void testGetProperty() throws Throwable {
        Object expected = smallObject.get("two", smallObject);
        CallSite site =
                Bootstrapper.bootstrap(
                        MethodHandles.lookup(),
                        "PROP:GET:two",
                        MethodType.fromMethodDescriptorString(
                                Bootstrapper.GET_PROP_SIGNATURE,
                                DynamicOperationsTest.class.getClassLoader()));
        MethodHandle invoker = site.dynamicInvoker();
        // Test 10 times so account for optimizations
        for (int i = 0; i < 10; i++) {
            Object result = invoker.invokeWithArguments(smallObject, cx, scope);
            assertEquals(expected, result);
        }
    }
}
