package org.mozilla.javascript.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mozilla.javascript.Slot;

public class SlotKeyTest {
    @Test
    public void testStringKey() {
        Slot.Key k = new Slot.Key("foo");
        assertTrue(k.isName());
        assertFalse(k.isIndex());
        assertEquals("foo", k.getName());
        assertEquals("foo", k.toObject());
        assertEquals("foo", k.toString());
        assertTrue(k.equals(new Slot.Key("foo")));
        assertEquals(0, k.compareTo(new Slot.Key("foo")));
        assertFalse(k.equals(new Slot.Key("bar")));
        assertTrue(k.compareTo(new Slot.Key("bar")) > 0);
        // Numbers always compare before strings
        assertFalse(k.equals(new Slot.Key(10)));
        assertTrue(k.compareTo(new Slot.Key(10)) > 0);
    }

    @Test
    public void testIntKey() {
        Slot.Key k = new Slot.Key(10);
        assertFalse(k.isName());
        assertTrue(k.isIndex());
        assertEquals(10, k.getIndex());
        assertEquals(10, k.toObject());
        assertEquals("10", k.toString());
        assertTrue(k.equals(new Slot.Key(10)));
        assertEquals(0, k.compareTo(new Slot.Key(10)));
        assertFalse(k.equals(new Slot.Key(11)));
        assertTrue(k.compareTo(new Slot.Key(5)) > 0);
        assertTrue(k.compareTo(new Slot.Key(20)) < 0);
        // Numbers always compare before strings
        assertFalse(k.equals(new Slot.Key("10")));
        assertTrue(k.compareTo(new Slot.Key("10")) < 0);
    }
}
