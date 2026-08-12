package org.mozilla.javascript.benchmarks;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ByteBufferBenchmark {
    private static final int ARRAY_SIZE = 100;
    private static final int BYTE_LOC = 10;
    private static final int INT_LOC = 2;

    @Benchmark
    public Object add() {
        int val = 2 + 2;
        return val;
    }

    private static int add(int a, int b) {
        return a + b;
    }

    private static int mathOp(int a, int b, BiFunction<Integer, Integer, Integer> f) {
        return f.apply(a, b);
    }

    @Benchmark
    public Object addCallout() {
        int val = mathOp(2, 2, ByteBufferBenchmark::add);
        return val;
    }

    @Benchmark
    public Object addCallout2() {
        int val = mathOp(2, 2, Integer::sum);
        return val;
    }

    @State(Scope.Thread)
    public static class ByteBufferState {
        private ByteBuffer array;

        @Setup(Level.Trial)
        public void setup() {
            array = ByteBuffer.allocate(ARRAY_SIZE);
            array.put(BYTE_LOC, (byte) 123);
            array.putInt(INT_LOC, 12345);
        }
    }

    @Benchmark
    public Object getByteBuffer(ByteBufferState state) {
        if (state.array.get(BYTE_LOC) != 123) {
            throw new AssertionError();
        }
        return 123;
    }

    @Benchmark
    public Object setByteBuffer(ByteBufferState state) {
        state.array.put(BYTE_LOC, (byte) 123);
        return 456;
    }

    @Benchmark
    public Object getIntBuffer(ByteBufferState state) {
        if (state.array.getInt(INT_LOC) != 12345) {
            throw new AssertionError();
        }
        return 12345;
    }

    @Benchmark
    public Object setIntBuffer(ByteBufferState state) {
        state.array.putInt(INT_LOC, 12345);
        return 12345;
    }

    @State(Scope.Thread)
    public static class ByteBufferHandleState {
        private ByteBuffer array;
        private static final VarHandle intVh =
                MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

        @Setup(Level.Trial)
        public void setup() {
            array = ByteBuffer.allocate(ARRAY_SIZE);
            array.put(BYTE_LOC, (byte) 123);
            array.putInt(INT_LOC, 12345);
        }
    }

    @Benchmark
    public Object getByteBufferHandle(ByteBufferHandleState state) {
        int i = state.array.get(BYTE_LOC);
        if (i != 123) {
            throw new AssertionError();
        }
        return 123;
    }

    @Benchmark
    public Object setByteBufferHandle(ByteBufferHandleState state) {
        state.array.put(BYTE_LOC, (byte) 123);
        return 123;
    }

    @Benchmark
    public Object getIntBufferHandle(ByteBufferHandleState state) {
        int i = (int) state.intVh.get(state.array, INT_LOC);
        if (i != 12345) {
            throw new AssertionError();
        }
        return i;
    }

    @Benchmark
    public Object setIntBufferHandle(ByteBufferHandleState state) {
        state.intVh.set(state.array, INT_LOC, 12345);
        return 12345;
    }

    @State(Scope.Thread)
    public static class DirectByteBufferHandleState {
        private ByteBuffer array;
        private static final VarHandle intVh =
                MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

        @Setup(Level.Trial)
        public void setup() {
            array = ByteBuffer.allocateDirect(ARRAY_SIZE);
            array.put(BYTE_LOC, (byte) 123);
            array.putInt(INT_LOC, 12345);
        }
    }

    @Benchmark
    public Object getByteDirect(DirectByteBufferHandleState state) {
        int i = state.array.get(BYTE_LOC);
        if (i != 123) {
            throw new AssertionError();
        }
        return 123;
    }

    @Benchmark
    public Object setByteDirect(DirectByteBufferHandleState state) {
        state.array.put(BYTE_LOC, (byte) 123);
        return 123;
    }

    @Benchmark
    public Object getIntDirectHandle(DirectByteBufferHandleState state) {
        int i = (int) state.intVh.get(state.array, INT_LOC);
        if (i != 12345) {
            throw new AssertionError();
        }
        return 12345;
    }

    @Benchmark
    public Object setIntDirectHandle(DirectByteBufferHandleState state) {
        state.intVh.set(state.array, INT_LOC, 12345);
        return 12345;
    }

    @Benchmark
    public Object getIntDirectHandleVolatile(DirectByteBufferHandleState state) {
        int i = (int) state.intVh.getVolatile(state.array, INT_LOC);
        if (i != 12345) {
            throw new AssertionError();
        }
        return 12345;
    }

    @Benchmark
    public Object setIntDirectHandleVolatile(DirectByteBufferHandleState state) {
        state.intVh.setVolatile(state.array, INT_LOC, 12345);
        return 12345;
    }
}
