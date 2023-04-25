package org.mozilla.javascript.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mozilla.javascript.PropertyMap;

public class PropertyMapTest {
    @Test
    public void testLevelOne() {
        PropertyMap a = PropertyMap.ROOT.add("a");
        PropertyMap b = PropertyMap.ROOT.add("b");
        assertNotEquals(a, b);
        PropertyMap a1 = PropertyMap.ROOT.add("a");
        assertEquals(a1, a);
        assertEquals(0, a.find("a"));
        assertEquals(-1, a.find("b"));
        assertEquals(0, b.find("b"));
        assertEquals(-1, b.find("a"));
    }

    @Test
    public void testLevelThree() {
        PropertyMap a1 = PropertyMap.ROOT.add("a");
        PropertyMap a2 = a1.add("b");
        PropertyMap a3 = a2.add("c");
        PropertyMap a1a = PropertyMap.ROOT.add("a");
        assertEquals(a1, a1a);
        PropertyMap a2a = a1a.add("b");
        assertEquals(a2, a2a);
        PropertyMap a3a = a2a.add("c");
        assertEquals(a3, a3a);
        PropertyMap a4 = a3.add("d");
        assertNotEquals(a3, a4);

        assertEquals(0, a4.find("a"));
        assertEquals(1, a4.find("b"));
        assertEquals(2, a4.find("c"));
        assertEquals(3, a4.find("d"));
        assertEquals(-1, a4.find("e"));

        PropertyMap a3b = a4.remove("d");
        assertEquals(a3, a3b);
        assertNull(a4.remove("b"));
    }
}
