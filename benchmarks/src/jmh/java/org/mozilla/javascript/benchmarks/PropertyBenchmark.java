package org.mozilla.javascript.benchmarks;

import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class PropertyBenchmark {
    @State(Scope.Thread)
    public static class PropertyState {
        Context cx;
        Scriptable scope;

        Function create;
        Function createFieldByField;
        Function getName;
        Function check;
        Function loopVariable;
        Function loopConst;
        Function loopArray;

        Object array;
        Object object;

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
            check = (Function) ScriptableObject.getProperty(scope, "check");
            loopVariable = (Function) ScriptableObject.getProperty(scope, "loopVariable");
            loopConst = (Function) ScriptableObject.getProperty(scope, "loopConstant");
            loopArray = (Function) ScriptableObject.getProperty(scope, "loopArray");

            object = create.call(cx, scope, null, new Object[] {"testing"});
            Function createArray = (Function) ScriptableObject.getProperty(scope, "createArray");
            array = createArray.call(cx, scope, null, Context.emptyArgs);
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
    public Object addTwoProperties(PropertyBenchmark.PropertyState state) {
        return state.check.call(state.cx, state.scope, null, new Object[] {state.object});
    }

    @Benchmark
    public Object loopVariable(PropertyBenchmark.PropertyState state) {
        return state.loopVariable.call(state.cx, state.scope, null, Context.emptyArgs);
    }

    @Benchmark
    public Object loopConstant(PropertyBenchmark.PropertyState state) {
        return state.loopConst.call(state.cx, state.scope, null, Context.emptyArgs);
    }

    @Benchmark
    public Object loopArray(PropertyBenchmark.PropertyState state) {
        return state.loopArray.call(state.cx, state.scope, null, new Object[] {state.array});
    }
}
