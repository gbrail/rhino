package org.mozilla.javascript.benchmarks;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.optimizer.DynamicOperations;
import org.openjdk.jmh.annotations.*;

public class PropertyBenchmark {
    @State(Scope.Thread)
    public static class PropertyState {
        Context cx;
        Scriptable scope;

        Function create;
        Function createAlternate;
        Function createFieldByField;
        Function getName;
        Function check;

        Object object;
        Object alternateObject;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            cx = Context.enter();
            cx.setOptimizationLevel(9);
            cx.setLanguageVersion(Context.VERSION_ES6);
            scope = cx.initStandardObjects();

            try (FileReader rdr =
                    new FileReader("testsrc/benchmarks/micro/property-benchmarks.js")) {
                cx.evaluateReader(scope, rdr, "property-benchmarks.js", 1, null);
            }
            create = (Function) ScriptableObject.getProperty(scope, "createObject");
            createAlternate =
                    (Function) ScriptableObject.getProperty(scope, "createAlternateObject");
            createFieldByField =
                    (Function) ScriptableObject.getProperty(scope, "createObjectFieldByField");
            getName = (Function) ScriptableObject.getProperty(scope, "getName");
            check = (Function) ScriptableObject.getProperty(scope, "check");

            object = create.call(cx, scope, null, new Object[] {"testing"});
            alternateObject = createAlternate.call(cx, scope, null, new Object[] {"123"});
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            cx.close();
            FileOutputStream out = new FileOutputStream("benchmark-stats.txt", true);
            DynamicOperations.dump(out);
            out.close();
        }
    }

    /*
    @Benchmark
    public Object createObject(PropertyBenchmark.PropertyState state) {
        Object obj = state.create.call(state.cx, state.scope, null, new Object[] {"testing"});
        String name =
                ScriptRuntime.toString(
                        state.getName.call(state.cx, state.scope, null, new Object[] {obj}));
        if (!"testing".equals(name)) {
            throw new AssertionError("Expected testing");
        }
        return name;
    }

    @Benchmark
    public Object createObjectFieldByField(PropertyBenchmark.PropertyState state) {
        Object obj =
                state.createFieldByField.call(
                        state.cx, state.scope, null, new Object[] {"testing"});
        String name =
                ScriptRuntime.toString(
                        state.getName.call(state.cx, state.scope, null, new Object[] {obj}));
        if (!"testing".equals(name)) {
            throw new AssertionError("Expected testing");
        }
        return name;
    }
    */

    @Benchmark
    @OperationsPerInvocation(100)
    public Object getOneProperty(PropertyBenchmark.PropertyState state) {
        state.getName.call(state.cx, state.scope, null, new Object[] {state.object, 1});
        String name =
                ScriptRuntime.toString(
                        state.getName.call(
                                state.cx, state.scope, null, new Object[] {state.object, 99}));
        if (!"testing".equals(name)) {
            throw new AssertionError("Expected testing");
        }
        return name;
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object getOnePropertyOtherObject(PropertyBenchmark.PropertyState state) {
        state.getName.call(state.cx, state.scope, null, new Object[] {state.object, 1});
        String name =
                ScriptRuntime.toString(
                        state.getName.call(
                                state.cx,
                                state.scope,
                                null,
                                new Object[] {state.alternateObject, 99}));
        if (!"123".equals(name)) {
            throw new AssertionError("Expected \"123\"");
        }
        return name;
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object addTwoProperties(PropertyBenchmark.PropertyState state) {
        state.check.call(state.cx, state.scope, null, new Object[] {state.object, 1});
        return state.check.call(state.cx, state.scope, null, new Object[] {state.object, 99});
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object addTwoPropertiesOtherObject(PropertyBenchmark.PropertyState state) {
        state.check.call(state.cx, state.scope, null, new Object[] {state.object, 1});
        return state.check.call(
                state.cx, state.scope, null, new Object[] {state.alternateObject, 99});
    }
}
