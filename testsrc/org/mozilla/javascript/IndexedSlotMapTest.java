package org.mozilla.javascript;

import static org.junit.Assert.*;

import org.junit.Test;

public class IndexedSlotMapTest {
    @Test
    public void testFastProp() {
        SlotMap map = new IndexedSlotMap();
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
        SlotMap map = new IndexedSlotMap();
        Slot s1 = map.modify("one", 0, 0);
        s1.value = 1;
        SlotMap.FastModifyResult rr1 = map.modifyAndGetFastKey("one", 0, 0);
        assertNotNull(rr1.key);
        rr1.slot.value = 2;
        assertEquals(map.query("one", 0).value, 2);
        s1 = map.modifyFast(rr1.key);
        assertNotEquals(s1, SlotMap.NOT_A_FAST_PROPERTY);
    }

    @Test
    public void testMatchingTrees() {
        SlotMap m1 = new IndexedSlotMap();
        m1.modify("one", 0, 0).value = 1;
        m1.modify("two", 0, 0).value = 2;
        m1.modify("three", 0, 0).value = 3;

        SlotMap m2 = new IndexedSlotMap();
        m2.modify("one", 0, 0).value = 10;
        m2.modify("two", 0, 0).value = 20;
        m2.modify("three", 0, 0).value = 30;

        // With two maps with the same key order, a FastKey can be shared
        SlotMap.FastKey k1 = m1.getFastKey("one", 0);
        assertEquals(m1.queryFast(k1).value, 1);
        assertEquals(m2.queryFast(k1).value, 10);
        SlotMap.FastKey k3 = m1.getFastKey("three", 0);
        assertEquals(m1.queryFast(k3).value, 3);
        assertEquals(m2.queryFast(k3).value, 30);
    }

    @Test
    public void testAlmostMatchingTrees() {
        SlotMap m1 = new IndexedSlotMap();
        m1.modify("one", 0, 0).value = 1;
        m1.modify("two", 0, 0).value = 2;
        m1.modify("three", 0, 0).value = 3;

        SlotMap m2 = new IndexedSlotMap();
        m2.modify("one", 0, 0).value = 10;
        m2.modify("three", 0, 0).value = 30;
        m2.modify("two", 0, 0).value = 20;

        // A FastKey can't be shared when maps are in a different order
        SlotMap.FastKey k2 = m1.getFastKey("two", 0);
        assertEquals(m1.queryFast(k2).value, 2);
        assertEquals(m2.queryFast(k2), SlotMap.NOT_A_FAST_PROPERTY);
        SlotMap.FastKey k3 = m1.getFastKey("three", 0);
        assertEquals(m1.queryFast(k3).value, 3);
        assertEquals(m2.queryFast(k3), SlotMap.NOT_A_FAST_PROPERTY);

        // A FastKey should be sharable when maps share a root
        SlotMap.FastKey k1 = m1.getFastKey("one", 0);
        assertEquals(m1.queryFast(k1).value, 1);
        assertEquals(m2.queryFast(k1).value, 10);
    }
}
