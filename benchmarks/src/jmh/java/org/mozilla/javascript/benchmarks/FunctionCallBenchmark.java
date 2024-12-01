package org.mozilla.javascript.benchmarks;

import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.LambdaFunction;
import org.mozilla.javascript.LambdaFunction0;
import org.mozilla.javascript.LambdaFunction1;
import org.mozilla.javascript.LambdaFunction2;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FunctionCallBenchmark {
    @State(Scope.Thread)
    public static class CallState {
        Context cx;
        Scriptable scope;
        Function callZero;
        Function callOne;
        Function callTwo;
        Function callZeroOpt;
        Function callOneOpt;
        Function callTwoOpt;

        @Setup(Level.Trial)
        public void init() throws IOException {
            cx = Context.enter();
            cx.setOptimizationLevel(9);
            cx.setLanguageVersion(Context.VERSION_ES6);
            scope = cx.initStandardObjects();

            scope.put(
                    "callZero",
                    scope,
                    new LambdaFunction(
                            scope,
                            "callZero",
                            0,
                            (Context lcx, Scriptable ls, Scriptable thisObj, Object[] args) -> 0));
            scope.put(
                    "callOne",
                    scope,
                    new LambdaFunction(
                            scope,
                            "callOne",
                            1,
                            (Context lcx, Scriptable ls, Scriptable thisObj, Object[] args) ->
                                    args.length >= 1 ? args[0] : Undefined.instance));
            scope.put(
                    "callTwo",
                    scope,
                    new LambdaFunction(
                            scope,
                            "callTwo",
                            2,
                            (Context lcx, Scriptable ls, Scriptable thisObj, Object[] args) -> {
                                Object arg1 = (args.length >= 1 ? args[0] : Undefined.instance);
                                Object arg2 = (args.length >= 2 ? args[1] : Undefined.instance);
                                return ScriptRuntime.toInt32(arg1) + ScriptRuntime.toInt32(arg2);
                            }));
            scope.put(
                    "callZeroOpt",
                    scope,
                    new LambdaFunction0(
                            scope,
                            "callZeroOpt",
                            (Context lcx, Scriptable ls, Scriptable thisObj) -> 0));
            scope.put(
                    "callOneOpt",
                    scope,
                    new LambdaFunction1(
                            scope,
                            "callOneOpt",
                            (Context lcx, Scriptable ls, Scriptable thisObj, Object arg) -> arg));
            scope.put(
                    "callTwoOpt",
                    scope,
                    new LambdaFunction2(
                            scope,
                            "callTwoOpt",
                            (Context lcx,
                                    Scriptable ls,
                                    Scriptable thisObj,
                                    Object arg1,
                                    Object arg2) ->
                                    ScriptRuntime.toInt32(arg1) + ScriptRuntime.toInt32(arg2)));

            try (FileReader rdr =
                    new FileReader("testsrc/benchmarks/micro/function-benchmarks.js")) {
                cx.evaluateReader(scope, rdr, "test.js", 1, null);
            }

            callZero = (Function) scope.get("testCallZero", scope);
            callOne = (Function) scope.get("testCallOne", scope);
            callTwo = (Function) scope.get("testCallTwo", scope);
            callZeroOpt = (Function) scope.get("testCallZeroOpt", scope);
            callOneOpt = (Function) scope.get("testCallOneOpt", scope);
            callTwoOpt = (Function) scope.get("testCallTwoOpt", scope);
        }

        @TearDown(Level.Trial)
        public void close() {
            Context.exit();
        }
    }

    @Benchmark
    public void callZeroArgs(CallState state) {
        state.callZero.call(state.cx, state.scope, null, ScriptRuntime.emptyArgs);
    }

    @Benchmark
    public void callOneArgs(CallState state) {
        state.callOne.call(state.cx, state.scope, null, ScriptRuntime.emptyArgs);
    }

    @Benchmark
    public void callTwoArgs(CallState state) {
        state.callTwo.call(state.cx, state.scope, null, ScriptRuntime.emptyArgs);
    }

    @Benchmark
    public void callZeroArgsOpt(CallState state) {
        state.callZeroOpt.call(state.cx, state.scope, null, ScriptRuntime.emptyArgs);
    }

    @Benchmark
    public void callOneArgsOpt(CallState state) {
        state.callOneOpt.call(state.cx, state.scope, null, ScriptRuntime.emptyArgs);
    }

    @Benchmark
    public void callTwoArgsOpt(CallState state) {
        state.callTwoOpt.call(state.cx, state.scope, null, ScriptRuntime.emptyArgs);
    }
}
