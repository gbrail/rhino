package org.mozilla.javascript.benchmarks;

import java.io.FileReader;
import java.io.IOException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.openjdk.jmh.annotations.*;

public class PropertyBenchmark {
    @State(Scope.Thread)
    public static class PropertyState {
        Context cx;
        Scriptable scope;

        Function create;
        Function createFieldByField;
        Function getName;
        Function getThisName;
        Function check;
        Function checkThis;
        Function setName;
        Function setThisName;

        Scriptable object;

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
            createFieldByField =
                    (Function) ScriptableObject.getProperty(scope, "createObjectFieldByField");
            getName = (Function) ScriptableObject.getProperty(scope, "getName");
            getThisName = (Function) ScriptableObject.getProperty(scope, "getThisName");
            check = (Function) ScriptableObject.getProperty(scope, "check");
            checkThis = (Function) ScriptableObject.getProperty(scope, "checkThis");
            setName = (Function) ScriptableObject.getProperty(scope, "setName");
            setThisName = (Function) ScriptableObject.getProperty(scope, "setThisName");

            object =
                    (Scriptable) createFieldByField.call(cx, scope, null, new Object[] {"testing"});
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            cx.close();
        }
    }

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

    @Benchmark
    public Object getOneProperty(PropertyBenchmark.PropertyState state) {
        String name =
                ScriptRuntime.toString(
                        state.getName.call(
                                state.cx, state.scope, null, new Object[] {state.object}));
        if (!"testing".equals(name)) {
            throw new AssertionError("Expected testing");
        }
        return name;
    }

    @Benchmark
    public Object getThisOneProperty(PropertyBenchmark.PropertyState state) {
        String name =
                ScriptRuntime.toString(
                        state.getThisName.call(
                                state.cx, state.scope, state.object, ScriptRuntime.emptyArgs));
        if (!"testing".equals(name)) {
            throw new AssertionError("Expected testing");
        }
        return name;
    }

    @Benchmark
    public Object addTwoProperties(PropertyBenchmark.PropertyState state) {
        return state.check.call(state.cx, state.scope, null, new Object[] {state.object});
    }

    @Benchmark
    public Object addThisTwoProperties(PropertyBenchmark.PropertyState state) {
        return state.checkThis.call(state.cx, state.scope, state.object, ScriptRuntime.emptyArgs);
    }

    @Benchmark
    public Object setProperty(PropertyBenchmark.PropertyState state) {
        String name =
                ScriptRuntime.toString(
                        state.setName.call(
                                state.cx,
                                state.scope,
                                null,
                                new Object[] {state.object, "newName"}));
        if (!"newName".equals(name)) {
            throw new AssertionError("Expected newName");
        }
        return name;
    }

    @Benchmark
    public Object setThisProperty(PropertyBenchmark.PropertyState state) {
        String name =
                ScriptRuntime.toString(
                        state.setThisName.call(
                                state.cx, state.scope, state.object, new Object[] {"newName"}));
        if (!"newName".equals(name)) {
            throw new AssertionError("Expected newName");
        }
        return name;
    }
}
