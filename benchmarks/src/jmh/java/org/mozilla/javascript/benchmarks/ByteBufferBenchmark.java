package org.mozilla.javascript.benchmarks;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ByteBufferBenchmark {
  private static final int ARRAY_SIZE = 100;
  private static final int BYTE_LOC = 10;
  private static final int INT_LOC = 4;

  @State(Scope.Thread)
  public static class ByteBufferState {
    private static final VarHandle intVhBe =
        MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle intVhLe =
        MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    private ByteBuffer array;

    @Param("false")
    private boolean littleEndian;
    @Param("false")
    private boolean direct;

    @Setup(Level.Trial)
    public void setup() {
      array = direct ? ByteBuffer.allocateDirect(ARRAY_SIZE) : ByteBuffer.allocate(ARRAY_SIZE);
      array.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
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

  @Benchmark
  public Object getIntBufferHandle(ByteBufferState state) {
    int i;
    if (state.littleEndian) {
      i = (int) ByteBufferState.intVhLe.get(state.array, INT_LOC);
    } else {
      i = (int) ByteBufferState.intVhBe.get(state.array, INT_LOC);
    }
    if (i != 12345) {
      throw new AssertionError();
    }
    return i;
  }

  @Benchmark
  public Object setIntBufferHandle(ByteBufferState state) {
    if (state.littleEndian) {
      ByteBufferState.intVhLe.set(state.array, INT_LOC, 12345);
    } else {
      ByteBufferState.intVhBe.set(state.array, INT_LOC, 12345);
    }
    return 12345;
  }

  @Benchmark
  public Object addIntBuffer(ByteBufferState state) {
    synchronized (state) {
      int v = state.array.getInt(INT_LOC);
      v += 1;
      state.array.putInt(INT_LOC, v);
      return v;
    }
  }

  @Benchmark
  public Object addIntBufferHandle(ByteBufferState state) {
    int v;
    if (state.littleEndian) {
      v = (int) ByteBufferState.intVhLe.getAndAdd(state.array, INT_LOC, 1);
    } else {
      v = (int) ByteBufferState.intVhBe.getAndAdd(state.array, INT_LOC, 1);
    }
    return v;
  }
}
