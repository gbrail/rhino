package org.mozilla.javascript.benchmarks;

import java.util.Random;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.HashSlotMap;
import org.mozilla.javascript.PropertyMapSlotMap;
import org.mozilla.javascript.Slot;
import org.mozilla.javascript.SlotMap;
import org.openjdk.jmh.annotations.*;

public class SlotMapBenchmark {
    // Fixed seed for repeatability
    private static final Random rand = new Random(0);

    @State(Scope.Thread)
    public static class HashState {
        final HashSlotMap emptyMap = new HashSlotMap();
        final HashSlotMap size10Map = new HashSlotMap();
        final HashSlotMap size100Map = new HashSlotMap();
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
    @OperationsPerInvocation(100)
    public Object hashInsert1Key(HashState state) {
        Slot newSlot = null;
        for (int i = 0; i < 100; i++) {
            newSlot = state.emptyMap.modify(new Slot.Key(state.randomKeys[i]), 0);
        }
        if (newSlot == null) {
            throw new AssertionError();
        }
        return newSlot;
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object hashQueryKey10Entries(HashState state) {
        Slot slot = null;
        for (int i = 0; i < 100; i++) {
            slot = state.size10Map.query(new Slot.Key(state.size10LastKey));
        }
        if (slot == null) {
            throw new AssertionError();
        }
        return slot;
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object hashQueryKey100Entries(HashState state) {
        Slot slot = null;
        for (int i = 0; i < 100; i++) {
            slot = state.size100Map.query(new Slot.Key(state.size100LastKey));
        }
        if (slot == null) {
            throw new AssertionError();
        }
        return slot;
    }

    @State(Scope.Thread)
    public static class MapState {
        PropertyMapSlotMap emptyMap;
        PropertyMapSlotMap size10Map;
        PropertyMapSlotMap size100Map;
        final String[] randomKeys = new String[100];
        String size100LastKey;
        String size10LastKey;
        Context cx;

        @Setup(Level.Trial)
        public void create() {
            cx = Context.enter();
            emptyMap = new PropertyMapSlotMap();
            size10Map = new PropertyMapSlotMap();
            size100Map = new PropertyMapSlotMap();
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

        @TearDown(Level.Trial)
        public void close() {
            Context.exit();
        }
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object mapInsert1Key(MapState state) {
        Slot newSlot = null;
        for (int i = 0; i < 100; i++) {
            newSlot = state.emptyMap.modify(new Slot.Key(state.randomKeys[i]), 0);
        }
        if (newSlot == null) {
            throw new AssertionError();
        }
        return newSlot;
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object mapQueryKey10Entries(MapState state) {
        Slot slot = null;
        for (int i = 0; i < 100; i++) {
            slot = state.size10Map.query(new Slot.Key(state.size10LastKey));
        }
        if (slot == null) {
            throw new AssertionError();
        }
        return slot;
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object mapQueryKey10EntriesCached(MapState state) {
        SlotMap.CacheableResult<Slot> result = state.size10Map.queryAndGetCacheInfo(new Slot.Key(state.size10LastKey));
        if (result.getPropertyMap() == null) {
            throw new AssertionError();
        }
        Slot slot = null;
        for (int i = 0; i < 100; i++) {
            slot = state.size10Map.queryFromCache(result.getIndex());
        }
        if (slot == null) {
            throw new AssertionError();
        }
        return slot;
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object mapQueryKey100Entries(MapState state) {
        Slot slot = null;
        for (int i = 0; i < 100; i++) {
            slot = state.size100Map.query(new Slot.Key(state.size100LastKey));
        }
        if (slot == null) {
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
        Slot slot = map.modify(new Slot.Key(key), 0);
        slot.setValue(key, null, null);
        return key;
    }
}
