package org.mozilla.javascript.benchmarks;

import java.util.concurrent.TimeUnit;
import org.mozilla.javascript.DToA;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.BigDecimalDtoA;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NumberFormatBenchmark {

    private static final double DENORMAL = 4.47118444E-314;

    @Benchmark
    public Object scriptRuntimePi() {
        return ScriptRuntime.toString(Math.PI);
    }

    @Benchmark
    public Object dToAPi() {
        StringBuilder buffer = new StringBuilder();
        DToA.JS_dtostr(buffer, 0, 0, Math.PI);
        return buffer.toString();
    }

    @Benchmark
    public Object bigDecimalPi() {
        return BigDecimalDtoA.numberToString(Math.PI);
    }

    @Benchmark
    public Object scriptRuntimeOne() {
        return ScriptRuntime.toString(1.0);
    }

    @Benchmark
    public Object dToAOne() {
        StringBuilder buffer = new StringBuilder();
        DToA.JS_dtostr(buffer, 0, 0, 1.0);
        return buffer.toString();
    }

    @Benchmark
    public Object bigDecimalOne() {
        return BigDecimalDtoA.numberToString(1.0);
    }
}
