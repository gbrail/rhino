package org.mozilla.javascript.benchmarks;

import java.util.concurrent.TimeUnit;

import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.dtoa.DoubleToDecimal;
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
    public Object decimalPi() {
        return DoubleToDecimal.toString(Math.PI);
    }

    @Benchmark
    public Object scriptRuntimeOne() {
        return ScriptRuntime.toString(1.0);
    }

    @Benchmark
    public Object decimalOne() {
        return DoubleToDecimal.toString(1.0);
    }

    @Benchmark
    public Object scriptRuntimeDenormal() {
        return ScriptRuntime.toString(DENORMAL);
    }

    @Benchmark
    public Object decimalDecnormal() {
        return DoubleToDecimal.toString(DENORMAL);
    }
}
