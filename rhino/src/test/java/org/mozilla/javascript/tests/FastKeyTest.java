package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * This is a "white box test" for fast property key support. It knows how the optimization works for
 * ShapedSlotMap. If we change the implementation we may need to change this test.
 */
public class FastKeyTest {
    private Context cx;
    private Scriptable scope;

    @BeforeEach
    public void init() {
        // This test only actually makes sense when we're in compiled mode
        cx = Context.enter();
        scope = cx.initStandardObjects();
    }

    @AfterEach
    public void terminate() {
        Context.exit();
    }

    @Test
    public void testEmpty() {
        // Empty object has no fast key
        var obj = (ScriptableObject) cx.newObject(scope);
        var fk = obj.getFastKey("test");
        // Assume this is supported. This test case may need to change if
        // fast key support is changed and not always implemented.
        assertNotNull(fk);
        assertFalse(fk.isPresent());
        // Fast key works with same empty object
        var obj2 = (ScriptableObject) cx.newObject(scope);
        assertTrue(fk.isSameShape(obj2));
    }

    @Test
    public void testOneProperty() {
        var obj = (ScriptableObject) cx.newObject(scope);
        obj.defineProperty("test", 1, 0);
        var fk = obj.getFastKey("test");
        assertNotNull(fk);
        assertTrue(fk.isPresent());
        assertEquals(1, obj.getPropertyFast(fk, obj));
        // Fast key works with same object
        assertTrue(fk.isSameShape(obj));
        // Fast key works with different object with same shape
        var obj2 = (ScriptableObject) cx.newObject(scope);
        obj2.defineProperty("test", 2, 0);
        assertTrue(fk.isSameShape(obj2));
        assertEquals(2, obj2.getPropertyFast(fk, obj2));
        // Fast key no longer works when shape changes
        obj2.defineProperty("test2", 3, 0);
        assertFalse(fk.isSameShape(obj2));
    }

    @Test
    public void testModifyOneExistingProperty() {
        var obj = (ScriptableObject) cx.newObject(scope);
        obj.defineProperty("test", 1, 0);
        var fk = obj.getFastWriteKey("test", 0);
        assertNotNull(fk);
        assertTrue(fk.isPresent());
        assertEquals(1, obj.getPropertyFast(fk, obj));
        obj.putPropertyFast("test", fk, obj, 2, true);
        assertEquals(2, obj.getPropertyFast(fk, obj));
    }

    @Test
    public void testModifyEmptyNewProperty() {
        // Insert a new key totally
        var obj = (ScriptableObject) cx.newObject(scope);
        var fk = obj.getFastWriteKey("test", 0);
        assertNotNull(fk);
        assertTrue(fk.isPresent());
        obj.putPropertyFast("test", fk, obj, 1, true);
        assertEquals(1, obj.get("test", obj));
        // Test that it works with a different empty object
        var obj2 = (ScriptableObject) cx.newObject(scope);
        assertTrue(fk.isSameShape(obj2));
        obj2.putPropertyFast("test", fk, obj2, 2, true);
        assertEquals(2, obj2.get("test", obj2));
        // Test that it does not work with a different shape
        obj2.defineProperty("test2", 3, 0);
        assertFalse(fk.isSameShape(obj2));
        // And a new object with a different shape
        var obj3 = (ScriptableObject) cx.newObject(scope);
        obj3.defineProperty("test2", 4, 0);
        assertFalse(fk.isSameShape(obj3));
    }

    @Test
    public void testThreeProperties() {
        var obj = (ScriptableObject) cx.newObject(scope);
        obj.defineProperty("test", 1, 0);
        obj.defineProperty("test2", 2, 0);
        obj.defineProperty("test3", 3, 0);
        var fk = obj.getFastKey("test");
        assertNotNull(fk);
        assertTrue(fk.isPresent());
        assertEquals(1, obj.getPropertyFast(fk, obj));
        var notFound = obj.getFastKey("notFound");
        assertNotNull(notFound);
        assertFalse(notFound.isPresent());
        // Fast key works with same object
        assertTrue(fk.isSameShape(obj));
        // Fast key works with different object with same shape
        var obj2 = (ScriptableObject) cx.newObject(scope);
        obj2.defineProperty("test", 6, 0);
        obj2.defineProperty("test2", 4, 0);
        obj2.defineProperty("test3", 5, 0);
        assertTrue(fk.isSameShape(obj2));
        assertEquals(6, obj2.getPropertyFast(fk, obj2));
        // Fast key does not work if the shape is different
        obj2.defineProperty("test4", 7, 0);
        assertFalse(fk.isSameShape(obj2));
        // Or if an object has same properties in a different order
        var obj3 = (ScriptableObject) cx.newObject(scope);
        obj3.defineProperty("test", 8, 0);
        obj3.defineProperty("test3", 9, 0);
        obj3.defineProperty("test2", 10, 0);
        assertFalse(fk.isSameShape(obj3));
    }

    @Test
    public void testModifyThreeExistingProperties() {
        var obj = (ScriptableObject) cx.newObject(scope);
        obj.defineProperty("test", 1, 0);
        obj.defineProperty("test2", 2, 0);
        obj.defineProperty("test3", 3, 0);
        var fk = obj.getFastKey("test");
        var fkw = obj.getFastWriteKey("test", 0);
        assertNotNull(fk);
        assertTrue(fk.isPresent());
        assertEquals(1, obj.getPropertyFast(fk, obj));
        obj.putPropertyFast("test", fkw, obj, 4, true);
        assertEquals(4, obj.getPropertyFast(fk, obj));
    }

    @Test
    public void testModifyThreeNewProperties() {
        var obj = (ScriptableObject) cx.newObject(scope);
        obj.defineProperty("test", 1, 0);
        obj.defineProperty("test2", 2, 0);
        obj.defineProperty("test3", 3, 0);
        var fk = obj.getFastWriteKey("test4", 0);
        assertNotNull(fk);
        assertTrue(fk.isPresent());
        var obj2 = (ScriptableObject) cx.newObject(scope);
        obj2.defineProperty("test", 1, 0);
        obj2.defineProperty("test2", 2, 0);
        obj2.defineProperty("test3", 3, 0);
        assertTrue(fk.isSameShape(obj2));
        obj2.putPropertyFast("test4", fk, obj2, 4, true);
        assertEquals(4, obj2.get("test4", obj2));
    }
}
