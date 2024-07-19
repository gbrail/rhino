package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ObjectShape;

public class ObjectShapeTest {
    private ObjectShape rootShape;

    @BeforeEach
    public void init() {
        rootShape = new ObjectShape();
    }

    @Test
    public void rootShape() {
        assertNull(rootShape.getParent());
        assertFalse(rootShape.getProperty(1).isPresent());
    }

    @Test
    public void fillOne() {
        // New key should create a new shape
        ObjectShape.Result r1 = rootShape.putProperty(1);
        assertTrue(r1.getShape().isPresent());
        assertEquals(0, r1.getIndex());
        ObjectShape s2 = r1.getShape().get();
        assertEquals(0, s2.getProperty(1).getAsInt());
        assertFalse(rootShape.getProperty(1).isPresent());

        // Putting same key should return same shape
        ObjectShape.Result r1a = s2.putProperty(1);
        assertFalse(r1a.getShape().isPresent());
        assertEquals(0, r1a.getIndex());

        // Same key should give the same shape
        ObjectShape.Result r2 = rootShape.putProperty(1);
        assertTrue(r2.getShape().isPresent());
        assertEquals(0, r2.getIndex());
        assertSame(r1.getShape().get(), r2.getShape().get());
    }

    @Test
    public void fillSeveral() {
        // Fill a map with some properties
        ObjectShape shape = rootShape;
        for (int i = 0; i < 10; i++) {
            ObjectShape.Result r = shape.putProperty(i);
            assertTrue(r.getShape().isPresent());
            assertEquals(i, r.getIndex());
            shape = r.getShape().get();
        }
        ObjectShape finalShape1 = shape;
        // All properties should be there
        for (int i = 0; i < 10; i++) {
            assertEquals(i, finalShape1.getProperty(i).getAsInt());
        }

        // Fill a second map with a second set of properties
        shape = rootShape;
        int j = 10;
        for (int i = 0; i < 10; i++) {
            ObjectShape.Result r = shape.putProperty(j);
            assertTrue(r.getShape().isPresent());
            assertEquals(i, r.getIndex());
            shape = r.getShape().get();
            j--;
        }
        ObjectShape finalShape2 = shape;
        // All properties should be there in another order
        j = 10;
        for (int i = 0; i < 10; i++) {
            assertEquals(i, finalShape2.getProperty(j).getAsInt());
            j--;
        }

        // They should not be the same
        assertNotSame(finalShape1, finalShape2);

        // Re-create the first map
        shape = rootShape;
        for (int i = 0; i < 10; i++) {
            ObjectShape.Result r = shape.putProperty(i);
            assertTrue(r.getShape().isPresent());
            assertEquals(i, r.getIndex());
            shape = r.getShape().get();
        }
        // It should be the same as the first map
        assertSame(finalShape1, shape);

        // Same for the second
        shape = rootShape;
        j = 10;
        for (int i = 0; i < 10; i++) {
            ObjectShape.Result r = shape.putProperty(j);
            assertTrue(r.getShape().isPresent());
            assertEquals(i, r.getIndex());
            shape = r.getShape().get();
            j--;
        }
        assertSame(finalShape2, shape);
    }
}
