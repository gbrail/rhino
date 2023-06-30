package org.mozilla.javascript;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class IndexedSlotMapTest {
    private SlotMap map;

    @Before
    public void init() {
        map = new IndexedSlotMap();
    }

    @Test
    public void testFastProp() {
        Slot s1 = map.modify("one", 0, 0);
        s1.value = 1;
        Slot s2 = map.modify("two", 0, 0);
        s2.value = 2;
        Slot s3 = map.modify("three", 0, 0);
        s3.value = 3;
        Slot s4 = map.modify(null, 0, 0);
        s4.value = 4;

        assertEquals(map.query("one", 0).value, 1);
        assertEquals(map.query("two", 0).value, 2);
        assertEquals(map.query("three", 0).value, 3);
        assertEquals(map.query(null, 0).value, 4);

        SlotMap.FastKey f1 = map.getFastKey("one", 0);
        assertNotNull(f1);
        SlotMap.FastKey f2 = map.getFastKey("two", 0);
        assertNotNull(f2);
        SlotMap.FastKey f3 = map.getFastKey("three", 0);
        assertNotNull(f3);
        SlotMap.FastKey f4 = map.getFastKey(null, 0);
        assertNotNull(f4);
        SlotMap.FastKey fm = map.getFastKey("missing", 0);
        assertNull(fm);

        assertEquals(map.queryFast(f1).value, 1);
        assertEquals(map.queryFast(f2).value, 2);
        assertEquals(map.queryFast(f3).value, 3);
        assertEquals(map.queryFast(f4).value, 4);
    }

    @Test
    public void testFastPropModifyExisting() {
        Slot s0 = map.modify("foo", 0, 0);
        s0.value = 111;

        // Modify existing property
        Slot s1 = map.modify("one", 0, 0);
        s1.value = 1;
        SlotMap.FastKey fk = map.getFastKey("one", 0);
        assertNotNull(fk);
        Slot s2 = map.modifyFast(fk);
        assertNotEquals(s2, SlotMap.NOT_A_FAST_PROPERTY);
        s2.value = 2;
        assertEquals(map.query("one", 0).value, 2);
        Slot s3 = map.modifyFast(fk);
        assertNotEquals(s3, SlotMap.NOT_A_FAST_PROPERTY);
        s3.value = 3;
        assertEquals(map.query("one", 0).value, 3);

        // Fast modification should work on same property map when modifying an existing key
        SlotMap map2 = new IndexedSlotMap();
        s0 = map2.modify("foo", 0, 0);
        s0.value = 111;
        s1 = map2.modify("one", 0, 0);
        s1.value = 2;
        s2 = map2.modifyFast(fk);
        assertNotEquals(s2, SlotMap.NOT_A_FAST_PROPERTY);
        s2.value = 3;
        assertEquals(map2.query("one", 0).value, 3);

        // Fast modification should fail on object with a different property map
        SlotMap map3 = new IndexedSlotMap();
        s0 = map3.modify("bar", 0, 0);
        s0.value = 111;
        s1 = map3.modify("one", 0, 0);
        s1.value = 2;
        s2 = map3.modifyFast(fk);
        assertEquals(s2, SlotMap.NOT_A_FAST_PROPERTY);
    }

    @Test
    public void testMatchingTrees() {
        map.modify("one", 0, 0).value = 1;
        map.modify("two", 0, 0).value = 2;
        map.modify("three", 0, 0).value = 3;

        SlotMap m2 = new IndexedSlotMap();
        m2.modify("one", 0, 0).value = 10;
        m2.modify("two", 0, 0).value = 20;
        m2.modify("three", 0, 0).value = 30;

        // With two maps with the same key order, a FastKey can be shared
        SlotMap.FastKey k1 = map.getFastKey("one", 0);
        assertEquals(map.queryFast(k1).value, 1);
        assertEquals(m2.queryFast(k1).value, 10);
        SlotMap.FastKey k3 = map.getFastKey("three", 0);
        assertEquals(map.queryFast(k3).value, 3);
        assertEquals(m2.queryFast(k3).value, 30);
    }

    @Test
    public void testAlmostMatchingTrees() {
        map.modify("one", 0, 0).value = 1;
        map.modify("two", 0, 0).value = 2;
        map.modify("three", 0, 0).value = 3;

        SlotMap m2 = new IndexedSlotMap();
        m2.modify("one", 0, 0).value = 10;
        m2.modify("three", 0, 0).value = 30;
        m2.modify("two", 0, 0).value = 20;

        // A FastKey can't be shared when maps are in a different order
        SlotMap.FastKey k2 = map.getFastKey("two", 0);
        assertEquals(map.queryFast(k2).value, 2);
        assertEquals(m2.queryFast(k2), SlotMap.NOT_A_FAST_PROPERTY);
        SlotMap.FastKey k3 = map.getFastKey("three", 0);
        assertEquals(map.queryFast(k3).value, 3);
        assertEquals(m2.queryFast(k3), SlotMap.NOT_A_FAST_PROPERTY);

        // A FastKey should be sharable when maps share a root
        SlotMap.FastKey k1 = map.getFastKey("one", 0);
        assertEquals(map.queryFast(k1).value, 1);
        assertEquals(m2.queryFast(k1).value, 10);
    }
}
