package org.mozilla.javascript.benchmarks;

import java.util.Random;
import org.mozilla.javascript.*;
import org.openjdk.jmh.annotations.*;

public class SlotMapBenchmark {
    // Fixed seed for repeatability
    private static final Random rand = new Random(0);

    @State(Scope.Thread)
    public static class EmbeddedState {
        final EmbeddedSlotMap emptyMap = new EmbeddedSlotMap();
        final EmbeddedSlotMap size10Map = new EmbeddedSlotMap();
        final EmbeddedSlotMap size100Map = new EmbeddedSlotMap();
        final String[] randomKeys = new String[100];
        String size100LastKey;
        String size10LastKey;

        @Setup(Level.Trial)
        public void create() {
            String lastKey = null;
            for (int i = 0; i < 10; i++) {
                lastKey = insertRandomEntry(size10Map);
            }
            size10LastKey = lastKey;
            for (int i = 0; i < 100; i++) {
                lastKey = insertRandomEntry(size100Map);
            }
            size100LastKey = lastKey;
            for (int i = 0; i < 100; i++) {
                randomKeys[i] = makeRandomString();
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(10)
    public Object embeddedInsert10Keys(EmbeddedState state) {
        Slot newSlot = null;
        for (int i = 0; i < 10; i++) {
            newSlot = state.emptyMap.modify(state.randomKeys[i], 0, 0);
        }
        if (newSlot == null) {
            throw new AssertionError();
        }
        return newSlot;
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object embeddedInsert100Keys(EmbeddedState state) {
        Slot newSlot = null;
        for (int i = 0; i < 100; i++) {
            newSlot = state.emptyMap.modify(state.randomKeys[i], 0, 0);
        }
        if (newSlot == null) {
            throw new AssertionError();
        }
        return newSlot;
    }

    @Benchmark
    @OperationsPerInvocation(1000)
    public Object embeddedQueryKey10Entries(EmbeddedState state) {
        Slot slot = null;
        for (int i = 0; i < 1000; i++) {
            slot = state.size10Map.query(state.size10LastKey, 0);
        }
        if (slot == null) {
            throw new AssertionError();
        }
        return slot;
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object embeddedQueryKey100Entries(EmbeddedState state) {
        Slot slot = null;
        for (int i = 0; i < 100; i++) {
            slot = state.size100Map.query(state.size100LastKey, 0);
        }
        if (slot == null) {
            throw new AssertionError();
        }
        return slot;
    }

    @State(Scope.Thread)
    public static class IndexedState {
        final IndexedSlotMap emptyMap = new IndexedSlotMap();
        final IndexedSlotMap size10Map = new IndexedSlotMap();
        final IndexedSlotMap size100Map = new IndexedSlotMap();
        final String[] randomKeys = new String[100];
        String size100LastKey;
        String size10LastKey;

        @Setup(Level.Trial)
        public void create() {
            String lastKey = null;
            for (int i = 0; i < 10; i++) {
                lastKey = insertRandomEntry(size10Map);
            }
            size10LastKey = lastKey;
            for (int i = 0; i < 100; i++) {
                lastKey = insertRandomEntry(size100Map);
            }
            size100LastKey = lastKey;
            for (int i = 0; i < 100; i++) {
                randomKeys[i] = makeRandomString();
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(10)
    public Object indexedInsert10Keys(IndexedState state) {
        Slot newSlot = null;
        for (int i = 0; i < 10; i++) {
            newSlot = state.emptyMap.modify(state.randomKeys[i], 0, 0);
        }
        if (newSlot == null) {
            throw new AssertionError();
        }
        return newSlot;
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object indexedInsert100Keys(IndexedState state) {
        Slot newSlot = null;
        for (int i = 0; i < 100; i++) {
            newSlot = state.emptyMap.modify(state.randomKeys[i], 0, 0);
        }
        if (newSlot == null) {
            throw new AssertionError();
        }
        return newSlot;
    }

    @Benchmark
    @OperationsPerInvocation(10000)
    public Object indexedQueryKey10Entries(IndexedState state) {
        Slot slot = null;
        for (int i = 0; i < 10000; i++) {
            slot = state.size10Map.query(state.size10LastKey, 0);
        }
        if (slot == null) {
            throw new AssertionError();
        }
        return slot;
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object indexedQueryKey100Entries(IndexedState state) {
        Slot slot = null;
        for (int i = 0; i < 100; i++) {
            slot = state.size100Map.query(state.size100LastKey, 0);
        }
        if (slot == null) {
            throw new AssertionError();
        }
        return slot;
    }

    @Benchmark
    @OperationsPerInvocation(1000000)
    public Object indexedQueryFastKey10Entries(IndexedState state) {
        SlotMap.FastKey key = state.size10Map.getFastKey(state.size10LastKey, 0);
        assert (key != null);
        Slot slot = null;
        for (int i = 0; i < 1000000; i++) {
            slot = state.size10Map.queryFast(key);
        }
        if (slot == null || slot == SlotMap.NOT_A_FAST_PROPERTY) {
            throw new AssertionError();
        }
        return slot;
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

    /** Insert a random key and value into the map */
    private static String insertRandomEntry(SlotMap map) {
        String key = makeRandomString();
        Slot slot = map.modify(key, 0, 0);
        slot.setValue(key, null, null);
        return key;
    }
}
