package org.mozilla.javascript.tests;

import static org.junit.Assert.*;

import java.util.HashMap;
import org.junit.Test;
import org.mozilla.javascript.StringKey;

public class IdentifierTest {
    @Test
    public void testEquality() {
        StringKey sk = new StringKey("foo");
        StringKey sk2 = new StringKey("foo");
        StringKey sk3 = new StringKey("bar");
        StringKey id1 = new StringKey("foo", 1);
        StringKey id2 = new StringKey("bar", 2);
        assertEquals(sk, sk2);
        assertEquals(sk, id1);
        assertEquals(id1, sk);
        assertEquals(id1, sk2);
        assertNotEquals(sk, sk3);
        assertEquals(sk3, id2);
        assertNotEquals(sk3, id1);
        assertNotEquals(id1, sk3);
        assertEquals(id2, sk3);
        assertEquals(sk.compareTo(sk2), 0);
        assertEquals(sk.compareTo(id1), 0);
        assertTrue(sk.compareTo(sk3) > 0);
        assertTrue(sk.compareTo(id2) > 0);
        assertTrue(sk3.compareTo(sk2) < 0);
        assertTrue(id2.compareTo(sk2) < 0);
        assertTrue(id2.compareTo(id1) < 0);
    }

    @Test
    public void testMixedHash() {
        HashMap<StringKey, Integer> m = new HashMap<>();
        m.put(new StringKey("foo"), 1);
        m.put(new StringKey("bar"), 2);
        m.put(new StringKey("baz", 3), 3);
        assertEquals(m.get(new StringKey("foo")), Integer.valueOf(1));
        assertEquals(m.get(new StringKey("foo", 1)), Integer.valueOf(1));
        assertEquals(m.get(new StringKey("bar")), Integer.valueOf(2));
        assertEquals(m.get(new StringKey("bar", 2)), Integer.valueOf(2));
        assertEquals(m.get(new StringKey("baz")), Integer.valueOf(3));
        assertEquals(m.get(new StringKey("baz", 3)), Integer.valueOf(3));
    }
}
