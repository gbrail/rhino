package org.mozilla.javascript.benchmarks;

import java.util.concurrent.TimeUnit;

import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.dtoa.BigDecimalToDecimal;
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
        var decimal = DoubleToDecimal.toDecimal(Math.PI);
        return decimal.toString();
    }

    @Benchmark
    public Object bigDecimalPi() {
        var decimal = BigDecimalToDecimal.toStandardDecimal(Math.PI);
        return decimal.toString();
    }

    @Benchmark
    public Object javaPi() {
        return Double.toString(Math.PI);
    }

    @Benchmark
    public Object scriptRuntimeOne() {
        return ScriptRuntime.toString(1.0);
    }

    @Benchmark
    public Object decimalOne() {
        var decimal = DoubleToDecimal.toDecimal(1.0);
        return decimal.toString();
    }

    @Benchmark
    public Object bigDecimalOne() {
        var decimal =  BigDecimalToDecimal.toStandardDecimal(1.0);
        return decimal.toString();
    }

    @Benchmark
    public Object javaOne() {
        return Double.toString(1.0);
    }

    @Benchmark
    public Object scriptRuntimeDenormal() {
        return ScriptRuntime.toString(DENORMAL);
    }

    @Benchmark
    public Object decimalDenormal() {
        var decimal = DoubleToDecimal.toDecimal(DENORMAL);
        return decimal.toString();
    }

    @Benchmark
    public Object bigDecimalDenormal() {
        var decimal = BigDecimalToDecimal.toStandardDecimal(DENORMAL);
        return decimal.toString();
    }

    @Benchmark
    public Object javaDenormal() {
        return Double.toString(DENORMAL);
    }
}
