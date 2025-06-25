package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Shape;

public class ShapeTest {
    private static final Random rand = new Random(0);

    @Test
    public void testFirstProperty() {
        var empty = new Shape();
        assertEquals(-1, empty.get("foo"));

        // Inserting new key gives new shape
        var r = empty.putIfAbsent("foo");
        assertTrue(r.isNewShape());
        assertEquals(0, r.getIndex());
        assertNotNull(r.getShape());
        var fooShape = r.getShape();
        assertEquals(0, fooShape.get("foo"));

        // Inserting same key gives same shape
        r = empty.putIfAbsent("foo");
        assertTrue(r.isNewShape());
        assertEquals(0, r.getIndex());
        assertNotNull(r.getShape());
        assertSame(fooShape, r.getShape());
    }

    @Test
    public void testPropertyTree() {
        var empty = new Shape();
        var s1 = empty.putIfAbsent("one").getShape();
        s1 = s1.putIfAbsent("two").getShape();
        s1 = s1.putIfAbsent("three").getShape();

        var s2 = empty.putIfAbsent("three").getShape();
        s2 = s2.putIfAbsent("two").getShape();
        s2 = s2.putIfAbsent("one").getShape();

        assertEquals(0, s1.get("one"));
        assertEquals(1, s1.get("two"));
        assertEquals(2, s1.get("three"));
        assertEquals(-1, s1.get("foo"));
        assertEquals(2, s2.get("one"));
        assertEquals(1, s2.get("two"));
        assertEquals(0, s2.get("three"));
        assertEquals(-1, s2.get("foo"));

        System.out.println("s1: " + s1);
        System.out.println("s2: " + s2);
    }

    @Test
    public void testMoreProperties() {
        String[] keys = new String[10];
        for (int i = 0; i < 10; i++) {
            keys[i] = makeRandomString();
        }
        var s = new Shape();
        for (int i = 0; i < 10; i++) {
            s = s.putIfAbsent(keys[i]).getShape();
        }
        for (int i = 0; i < 10; i++) {
            assertEquals(i, s.get(keys[i]));
            assertFalse(s.putIfAbsent(keys[i]).isNewShape());
        }
        System.out.println(s);
    }

    @Test
    public void testDifferentKeyTypes() {
        var s = new Shape();
        s = s.putIfAbsent("key").getShape();
        s = s.putIfAbsent(123).getShape();
        s = s.putIfAbsent(45.6).getShape();
        assertEquals(0, s.get("key"));
        assertEquals(1, s.get(123));
        assertEquals(2, s.get(45.6));
    }

    @Test
    public void testToStringNotNull() {
        var s = new Shape().putIfAbsent("foo").getShape();
        assertNotNull(s.toString());
    }

    @Test
    public void testShapeImmutability() {
        var s1 = new Shape().putIfAbsent("a").getShape();
        var s2 = s1.putIfAbsent("b").getShape();
        assertEquals(-1, s1.get("b"));
        assertEquals(1, s2.get("b"));
    }

    private static String makeRandomString() {
        int len = rand.nextInt(49) + 1;
        char[] c = new char[len];
        for (int cc = 0; cc < len; cc++) {
            c[cc] = (char) ('a' + rand.nextInt(25));
        }
        return new String(c);
    }
}
