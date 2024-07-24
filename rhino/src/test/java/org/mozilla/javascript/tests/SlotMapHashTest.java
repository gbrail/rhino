package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.EmbeddedSlotMap;
import org.mozilla.javascript.Slot;
import org.mozilla.javascript.SlotMap;
import org.mozilla.javascript.SlotMap.FastTester;

public class SlotMapHashTest {
    private SlotMap map;

    @BeforeEach
    public void init() {
        map = new EmbeddedSlotMap();
    }

    @Test
    public void emptyMap() {
        Optional<SlotMap.FastQueryResult> r = map.queryFastIndex("one", 0);
        assertFalse(r.isPresent());
    }

    @Test
    public void computeOne() {
        add(map, "one", 1);
        // Ensure that we can look up the properties using a fast index
        testFastIndex("one", 1);
        add(map, "two", 2);
        testFastIndex("one", 1);
        testFastIndex("two", 2);
        add(map, "three", 3);
        testFastIndex("one", 1);
        testFastIndex("two", 2);
        testFastIndex("three", 3);
        // Ensure that the key is tested
        Optional<SlotMap.FastQueryResult> r = map.queryFastIndex("three", 0);
        assertTrue(r.isPresent());
        assertTrue(r.get().test(map, "three", 0));
        assertFalse(r.get().test(map, "one", 0));
    }

    private void testFastIndex(String key, Object expected) {
        Optional<SlotMap.FastQueryResult> r = map.queryFastIndex(key, 0);
        assertTrue(r.isPresent());
        assertTrue(r.get().test(map, key, 0));
        Slot s = map.queryFast(r.get().getIndex());
        assertNotNull(s);
        assertEquals(expected, s.getValue(null));
    }

    @Test
    public void equivalentMaps() {
        SlotMap m1 = new EmbeddedSlotMap();
        add(m1, "foo", 1);
        add(m1, "bar", 2);
        add(m1, "baz", 3);
        SlotMap m2 = new EmbeddedSlotMap();
        add(m2, "foo", 1);
        add(m2, "bar", 2);
        add(m2, "baz", 3);
        // Two maps are equivalent if they have the same keys in the same order
        testEquivalentMaps(m1, m2, "foo", 1);
        testEquivalentMaps(m1, m2, "bar", 2);
        testEquivalentMaps(m1, m2, "baz", 3);
    }

    @Test
    public void differentOrder() {
        SlotMap m1 = new EmbeddedSlotMap(1);
        add(m1, "foo", 1);
        add(m1, "bar", 2);
        add(m1, "baz", 3);
        SlotMap m2 = new EmbeddedSlotMap(97);
        add(m2, "baz", 1);
        add(m2, "bar", 2);
        add(m2, "foo", 3);
        // Two maps are not equivalent if they have keys in a different order
        Optional<SlotMap.FastQueryResult> r = m1.queryFastIndex("foo", 0);
        assertTrue(r.isPresent());
        assertTrue(r.get().test(m1, "foo", 0));
        assertFalse(r.get().test(m2, "foo", 0));
    }

    private static void testEquivalentMaps(SlotMap m1, SlotMap m2, String key, Object expected) {
        Optional<SlotMap.FastQueryResult> r = m1.queryFastIndex(key, 0);
        assertTrue(r.isPresent());
        SlotMap.FastTester desc = r.get().getDiscriminator();
        assertTrue(desc.test(m1, key, 0));
        Slot s = m1.queryFast(r.get().getIndex());
        assertNotNull(s);
        assertEquals(expected, s.getValue(null));
        // Ensure that m2 has exact same result as m1
        assertTrue(desc.test(m2, key, 0));
        s = m2.queryFast(r.get().getIndex());
        assertNotNull(s);
        assertEquals(expected, s.getValue(null));
    }

    @Test
    public void updateOnAdd() {
        SlotMap m1 = new EmbeddedSlotMap(3);
        add(m1, "aaa", 1);
        add(m1, "bbb", 2);
        add(m1, "ccc", 3);
        add(m1, "ddd", 4);
        SlotMap m2 = new EmbeddedSlotMap(11);
        add(m2, "aaa", 1);
        add(m2, "bbb", 2);
        add(m2, "ccc", 3);
        add(m2, "ddd", 4);
        Optional<SlotMap.FastQueryResult> r = m1.queryFastIndex("aaa", 0);
        FastTester desc = r.get().getDiscriminator();
        assertTrue(desc.test(m1, "aaa", 0));
        assertTrue(desc.test(m2, "aaa", 0));
        // Add to one of the maps, which changes the hash of one of them...
        add(m1, "zzz", 5);
        // Now, descriminator of the original map no longer returns true because the
        // contents of that map changed and stuff might be in a different position.
        assertFalse(desc.test(m1, "aaa", 0));
        // However, other maps that formerly had the same hash still have it, so actually
        // the descriminator can return true for them!
        assertTrue(desc.test(m2, "aaa", 0));
    }

    @Test
    public void updateOnRemove() {
        SlotMap m1 = new EmbeddedSlotMap();
        add(m1, "aaa", 1);
        add(m1, "bbb", 2);
        add(m1, "ccc", 3);
        add(m1, "ddd", 4);
        SlotMap m2 = new EmbeddedSlotMap();
        add(m2, "aaa", 1);
        add(m2, "bbb", 2);
        add(m2, "ccc", 3);
        add(m2, "ddd", 4);
        Optional<SlotMap.FastQueryResult> r = m1.queryFastIndex("aaa", 0);
        FastTester desc = r.get().getDiscriminator();
        assertTrue(desc.test(m1, "aaa", 0));
        assertTrue(desc.test(m2, "aaa", 0));
        // Deleting an item also changes the hash, so that, as described above, the old
        // descriminator is still valid for the second, unmodified object, but not
        // for the first one!
        m1.compute("ccc", 0, (k, ix, e) -> null);
        assertFalse(desc.test(m1, "aaa", 0));
        assertTrue(desc.test(m2, "aaa", 0));
    }

    @Test
    public void bigObject() {
        final int size = 200;
        SlotMap m1 = new EmbeddedSlotMap(size);
        SlotMap m2 = new EmbeddedSlotMap(size);
        for (int i = 0; i < size; i++) {
            add(m1, String.valueOf(i), i);
            add(m2, String.valueOf(i), i);
        }
        // Verify all values
        int i;
        for (i = 0; i < size; i++) {
            Slot s = m1.query(String.valueOf(i), 0);
            assertNotNull(s);
            assertEquals(i, s.getValue(null));
        }
        for (i = 0; i < size; i++) {
            // All properties are compatible with a fast query for the same object
            Optional<SlotMap.FastQueryResult> r = m1.queryFastIndex(String.valueOf(i), 0);
            assertTrue(r.isPresent());
            assertTrue(r.get().test(m1, String.valueOf(i), 0));
            Slot s = m1.queryFast(r.get().getIndex());
            assertNotNull(s);
            assertEquals(i, s.getValue(null));
            if (i < EmbeddedSlotMap.NUM_FAST_PROPERTIES) {
                // The first few properties are compatible with the same query for a different
                // object with the same hash
                assertTrue(r.get().test(m2, String.valueOf(i), 0));
                s = m2.queryFast(r.get().getIndex());
                assertNotNull(s);
                assertEquals(i, s.getValue(null));
            } else {
                // The rest are only compatible for a fast query on the same object
                assertFalse(r.get().test(m2, String.valueOf(i), 0));
            }
        }
    }

    private static void add(SlotMap m, String key, Object value) {
        Slot s = m.modify(key, 0, 0);
        assertNotNull(s);
        s.setValue(value, null, null);
    }
}
