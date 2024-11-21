package org.mozilla.javascript.benchmarks;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.mozilla.javascript.ObjectShape;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ObjectShapeBenchmark {
    private static final Random rand = new Random(0);

    @State(Scope.Thread)
    public static class MapState {
        final String[] randomKeys = new String[100];
        final ObjectShape empty = ObjectShape.emptyMap();
        ObjectShape map10Keys;
        String lastKey;

        @Setup(Level.Trial)
        public void create() {
            for (int i = 0; i < 100; i++) {
                randomKeys[i] = makeRandomString();
            }
            map10Keys = ObjectShape.emptyMap();
            for (int i = 0; i < 10; i++) {
                lastKey = makeRandomString();
                map10Keys = map10Keys.add(lastKey);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(50)
    public Object insertKey(MapState state) {
        ObjectShape m = state.empty;
        for (int i = 0; i < 50; i++) {
            m = m.add(state.randomKeys[i]);
        }
        if (m.getPosition() != 49) {
            throw new AssertionError();
        }
        return m;
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object query10Keys(MapState state) {
        Object result = null;
        for (int i = 0; i < 100; i++) {
            int ix = state.map10Keys.find(state.lastKey);
            if (ix != 9) {
                throw new AssertionError();
            }
            result = Integer.valueOf(ix);
        }
        return result;
    }

    /** Make a new string between 1 and 50 characters out of random lower-case letters. */
    private static String makeRandomString() {
        int len = rand.nextInt(49) + 1;
        char[] c = new char[len];
        for (int cc = 0; cc < len; cc++) {
            c[cc] = (char) ('a' + rand.nextInt(25));
        }
        return new String(c);
    }
}
