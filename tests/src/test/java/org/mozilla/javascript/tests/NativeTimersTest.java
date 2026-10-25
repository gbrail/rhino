package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeTimers;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.testutils.TestSource;
import org.mozilla.javascript.testutils.Utils;
import org.mozilla.javascript.tools.shell.Global;

/** Test the setTimeout, clearTimeout, and queueMicrotask functions installed by NativeTimers. */
public class NativeTimersTest {
    private Context cx;
    private Global global;

    @BeforeEach
    public void init() {
        cx = Context.enter();
        cx.setLanguageVersion(Context.VERSION_ES6);
        global = new Global(cx);
        global.setFileLoadPrefix(TestSource.getPrefix());
        NativeTimers.init(global);
        global.put("TestsComplete", global, false);
    }

    @AfterEach
    public void cleanup() {
        cx.close();
    }

    public static Collection<Object[]> optLevels() {
        int[] optLevels = Utils.getTestOptLevels();
        ArrayList<Object[]> params = new ArrayList<>();
        for (int ol : optLevels) {
            params.add(new Object[] {ol});
        }
        return params;
    }

    /** Just make sure that a timeout fires. */
    @MethodSource("optLevels")
    @ParameterizedTest(name = "{index}, opt={0}")
    public void setImmediateTimeout(int optLevel) {
        initOptLevel(optLevel);
        runTest("setTimeout(() => { TestsComplete = true; });");
    }

    /** Ensure that parameters work and that "this" is the global object. */
    @MethodSource("optLevels")
    @ParameterizedTest(name = "{index}, opt={0}")
    public void checkTimeoutParameters(int optLevel) {
        initOptLevel(optLevel);
        runTest(
                "load('testsrc/assert.js');\n"
                        + "setTimeout((a, b) => { \n"
                        + "assertTrue(this != null);\n"
                        + "assertTrue(this != undefined);\n"
                        + "assertEquals('one', a);\n"
                        + "assertEquals(2, b);\n"
                        + "TestsComplete = true; }, 0, 'one', 2);");
    }

    /** Ensure that invalid arguments are rejected. */
    @MethodSource("optLevels")
    @ParameterizedTest(name = "{index}, opt={0}")
    public void checkTypeChecks(int optLevel) {
        initOptLevel(optLevel);
        runTest(
                "load('testsrc/assert.js');\n"
                        +
                        // Timeout must be a function and we do not support the "eval" style of
                        // setTimeout
                        "assertThrows(() => {setTimeout('x = 1;');}, TypeError);\n"
                        +
                        // Need to pass a function as the first argument
                        "assertThrows(() => {setTimeout();}, TypeError);\n"
                        +
                        // Need to pass a timer ID as the second argument
                        "assertThrows(() => {clearTimeout();}, TypeError);\n"
                        +
                        // OK because it is fine to clear a timeout with an invalid ID.
                        "clearTimeout(999);\n"
                        +
                        // OK because of the semantics of "ToInteger"
                        "setTimeout(() => {TestsComplete = true}, 'later');\n"
                        + "clearTimeout('hello');\n"
                        +
                        // queueMicrotask must be called with a function
                        "assertThrows(() => {queueMicrotask('x = 1;');}, TypeError);\n"
                        + "assertThrows(() => {queueMicrotask();}, TypeError);\n");
    }

    /** Make sure that timeouts are executed in absolute numerical order. */
    @MethodSource("optLevels")
    @ParameterizedTest(name = "{index}, opt={0}")
    public void setTimeoutOrder(int optLevel) {
        initOptLevel(optLevel);
        runTest(
                "load('testsrc/assert.js');\n"
                        + "var count = 0;\n"
                        + "setTimeout(() => { assertEquals(0, count++); });\n"
                        + "setTimeout(() => { assertEquals(1, count++); }, 1);\n"
                        + "setTimeout(() => { assertEquals(2, count); TestsComplete = true; }, 2);");
    }

    /** Make sure that timers can be cancelled. */
    @MethodSource("optLevels")
    @ParameterizedTest(name = "{index}, opt={0}")
    public void timerCancellation(int optLevel) {
        initOptLevel(optLevel);
        runTest(
                "load('testsrc/assert.js');\n"
                        + "let count = 0;\n"
                        + "let timer1 = setTimeout(() => { count++; });\n"
                        + "assertEquals('number', typeof timer1);\n"
                        + "setTimeout(() => { assertEquals(0, count++); }, 1);\n"
                        + "setTimeout(() => { assertEquals(1, count); TestsComplete = true; }, 2);\n"
                        + "clearTimeout(timer1);");
    }

    /** Make sure that timers can be nested inside other timers and so on. */
    @MethodSource("optLevels")
    @ParameterizedTest(name = "{index}, opt={0}")
    public void nestedTimers(int optLevel) {
        initOptLevel(optLevel);
        runTest(
                "load('testsrc/assert.js');\n"
                        + "let count = 0;\n"
                        + "setTimeout(() => {\n"
                        + "  setTimeout(() => {\n"
                        + "    setTimeout(() => {\n"
                        + "      assertEquals(2, count++); TestsComplete = true;\n"
                        + "    });"
                        + "    assertEquals(1, count++);\n"
                        + "  }, 2);\n"
                        + "  assertEquals(0, count++);\n"
                        + "});");
    }

    /**
     * A timer that is cleared must not run, and the event loop must still terminate.
     *
     * <p>Based on WPT "html/webappapis/timers/cleartimeout-clearinterval.any.js".
     */
    @MethodSource("optLevels")
    @ParameterizedTest(name = "{index}, opt={0}")
    public void clearTimeoutPreventsTimer(int optLevel) {
        initOptLevel(optLevel);
        runTest(
                "load('testsrc/assert.js');\n"
                        + "let ran = false;\n"
                        + "let id = setTimeout(() => { ran = true; }, 1);\n"
                        + "clearTimeout(id);\n"
                        + "setTimeout(() => { assertEquals(false, ran); TestsComplete = true; }, 5);");
    }

    /**
     * A negative delay is clamped to zero, so the timer must run before a 10ms timer.
     *
     * <p>Based on WPT "html/webappapis/timers/negative-settimeout.any.js".
     */
    @MethodSource("optLevels")
    @ParameterizedTest(name = "{index}, opt={0}")
    public void negativeDelayRunsFirst(int optLevel) {
        initOptLevel(optLevel);
        runTest(
                "load('testsrc/assert.js');\n"
                        + "var order = [];\n"
                        + "setTimeout(() => order.push('negative'), -100);\n"
                        + "setTimeout(() => order.push('ten'), 10);\n"
                        + "setTimeout(() => { assertEquals('negative,ten', order.join(',')); "
                        + "TestsComplete = true; }, 20);");
    }

    /**
     * A delay of 2^32 converts to 0, so the timer must run before a 100ms timer.
     *
     * <p>Based on WPT "html/webappapis/timers/type-long-settimeout.any.js".
     */
    @MethodSource("optLevels")
    @ParameterizedTest(name = "{index}, opt={0}")
    public void largeDelayCoerces(int optLevel) {
        initOptLevel(optLevel);
        runTest(
                "load('testsrc/assert.js');\n"
                        + "var order = [];\n"
                        + "setTimeout(() => order.push('large'), Math.pow(2, 32));\n"
                        + "setTimeout(() => order.push('hundred'), 100);\n"
                        + "setTimeout(() => { assertEquals('large,hundred', order.join(',')); "
                        + "TestsComplete = true; }, 200);");
    }

    /**
     * Omitting the delay is the same as passing 0, so the timer must run before a 100ms timer.
     *
     * <p>Based on WPT "html/webappapis/timers/missing-timeout-setinterval.any.js".
     */
    @MethodSource("optLevels")
    @ParameterizedTest(name = "{index}, opt={0}")
    public void missingDelayRunsFirst(int optLevel) {
        initOptLevel(optLevel);
        runTest(
                "load('testsrc/assert.js');\n"
                        + "var order = [];\n"
                        + "setTimeout(() => order.push('none'));\n"
                        + "setTimeout(() => order.push('hundred'), 100);\n"
                        + "setTimeout(() => { assertEquals('none,hundred', order.join(',')); "
                        + "TestsComplete = true; }, 200);");
    }

    /**
     * A microtask queued with queueMicrotask runs before any timer, at the next microtask
     * checkpoint.
     */
    @MethodSource("optLevels")
    @ParameterizedTest(name = "{index}, opt={0}")
    public void queueMicrotaskOrder(int optLevel) {
        initOptLevel(optLevel);
        runTest(
                "load('testsrc/assert.js');\n"
                        + "var order = [];\n"
                        + "queueMicrotask(() => order.push('micro'));\n"
                        + "setTimeout(() => order.push('ten'), 10);\n"
                        + "setTimeout(() => { assertEquals('micro,ten', order.join(',')); "
                        + "TestsComplete = true; }, 20);");
    }

    /**
     * A timer callback receives its arguments, as in WPT "html/webappapis/timers/arguments.html".
     */
    @MethodSource("optLevels")
    @ParameterizedTest(name = "{index}, opt={0}")
    public void timeoutArguments(int optLevel) {
        initOptLevel(optLevel);
        runTest(
                "load('testsrc/assert.js');\n"
                        + "setTimeout((a, b, c) => { assertEquals(1, a); assertEquals(2, b); "
                        + "assertEquals(3, c); TestsComplete = true; }, 0, 1, 2, 3);");
    }

    private void initOptLevel(int optLevel) {
        cx.setEvaluationMethod(Context.EvaluationMethod.forLevel(optLevel));
    }

    private void runTest(String script) {
        cx.evaluateString(global, script, "test.js", 1, null);
        try {
            cx.getTaskQueue().runUntilEmpty();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        assertTrue(ScriptRuntime.toBoolean(global.get("TestsComplete", global)));
    }
}
