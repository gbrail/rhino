package org.mozilla.javascript.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mozilla.javascript.PropertyMap;
import org.mozilla.javascript.Slot;

public class PropertyMapTest {
    @Test
    public void testLevelOne() {
        // First entry, creates new map with one entry
        PropertyMap root = new PropertyMap();
        PropertyMap.AddResult r1 = root.add(new Slot.Key("a"));
        assertTrue(r1.hasMap());
        assertEquals(0, r1.getIndex());
        PropertyMap m1 = r1.getMap();
        assertEquals(0, m1.get(new Slot.Key("a")));

        // Second entry, creates another new map with one entry
        PropertyMap.AddResult r2 = root.add(new Slot.Key("b"));
        assertTrue(r2.hasMap());
        assertEquals(0, r2.getIndex());
        PropertyMap m2 = r2.getMap();
        assertEquals(0, m2.get(new Slot.Key("b")));
        assertNotSame(m1, m2);

        // Same key as first map, returns same map
        PropertyMap.AddResult r3 = root.add(new Slot.Key("a"));
        assertTrue(r3.hasMap());
        assertEquals(0, r3.getIndex());
        PropertyMap m3 = r3.getMap();
        assertSame(m3, m1);
        assertNotSame(m3, m2);
    }

    @Test
    public void testLevelThree() {
        PropertyMap root = new PropertyMap();
        PropertyMap.AddResult r = root.add(new Slot.Key("a"));
        assertEquals(0, r.getIndex());
        PropertyMap m = r.getMap();
        assertEquals(0, m.get(new Slot.Key("a")));

        r = r.getMap().add(new Slot.Key("b"));
        assertEquals(1, r.getIndex());
        m = r.getMap();
        assertEquals(0, m.get(new Slot.Key("a")));
        assertEquals(1, m.get(new Slot.Key("b")));

        r = r.getMap().add(new Slot.Key("c"));
        assertEquals(2, r.getIndex());
        m = r.getMap();
        assertEquals(0, m.get(new Slot.Key("a")));
        assertEquals(1, m.get(new Slot.Key("b")));
        assertEquals(2, m.get(new Slot.Key("c")));

        // Build a new map that is slightly different
        r = root.add(new Slot.Key("a"));
        assertEquals(0, r.getIndex());
        m = r.getMap();
        assertEquals(0, m.get(new Slot.Key("a")));

        r = r.getMap().add(new Slot.Key("c"));
        assertEquals(1, r.getIndex());
        m = r.getMap();
        assertEquals(0, m.get(new Slot.Key("a")));
        assertEquals(1, m.get(new Slot.Key("c")));

        r = r.getMap().add(new Slot.Key("b"));
        assertEquals(2, r.getIndex());
        m = r.getMap();
        assertEquals(0, m.get(new Slot.Key("a")));
        assertEquals(1, m.get(new Slot.Key("c")));
        assertEquals(2, m.get(new Slot.Key("b")));
    }
}
