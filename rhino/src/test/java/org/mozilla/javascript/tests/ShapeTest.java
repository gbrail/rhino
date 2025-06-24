package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Shape;

public class ShapeTest {
    @Test
    public void testFirstProperty() {
        assertEquals(-1, Shape.EMPTY.get("foo"));

        // Inserting new key gives new shape
        var r = Shape.EMPTY.putIfAbsent("foo");
        assertTrue(r.isNewShape());
        assertEquals(0, r.getIndex());
        assertNotNull(r.getShape());
        var fooShape = r.getShape();
        assertEquals(0, fooShape.get("foo"));

        // Inserting same key gives same shape
        r = Shape.EMPTY.putIfAbsent("foo");
        assertTrue(r.isNewShape());
        assertEquals(0, r.getIndex());
        assertNotNull(r.getShape());
        assertSame(fooShape, r.getShape());
    }

    @Test
    public void testPropertyTree() {
        // Don't use "foo" here so we don't depend on test order
        var s1 = Shape.EMPTY.putIfAbsent("one").getShape();
        s1 = s1.putIfAbsent("two").getShape();
        s1 = s1.putIfAbsent("three").getShape();

        var s2 = Shape.EMPTY.putIfAbsent("three").getShape();
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
    }
}
