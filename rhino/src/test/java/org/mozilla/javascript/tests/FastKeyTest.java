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
    public void testQueryEmpty() {
        // Empty object has no fast key
        var obj = (ScriptableObject) cx.newObject(scope);
        var fk = obj.getFastKey("test");
        assertNull(fk);
    }

    @Test
    public void testQueryOne() {
        var obj = (ScriptableObject) cx.newObject(scope);
        obj.defineProperty("test", 1, 0);
        var fk = obj.getFastKey("test");
        assertNotNull(fk);
        assertTrue(fk.isCompatible(obj));
        assertEquals(1, obj.getPropertyFast(fk, obj));
        // Fast key works with different object with same shape
        var obj2 = (ScriptableObject) cx.newObject(scope);
        obj2.defineProperty("test", 2, 0);
        assertTrue(fk.isCompatible(obj2));
        assertEquals(2, obj2.getPropertyFast(fk, obj2));
        // Fast key no longer works when shape changes
        obj2.defineProperty("test2", 3, 0);
        assertFalse(fk.isCompatible(obj2));
    }

    @Test
    public void testQueryOnePrototype() {
        var proto1 = (ScriptableObject) cx.newObject(scope);
        var obj = (ScriptableObject) cx.newObject(scope);
        obj.setPrototype(proto1);
        obj.defineProperty("test", 1, 0);
        var fk = obj.getFastKey("test");
        assertNotNull(fk);
        assertTrue(fk.isCompatible(obj));
        assertEquals(1, obj.getPropertyFast(fk, obj));
        // Fast key works with different object with same shape
        var obj2 = (ScriptableObject) cx.newObject(scope);
        obj2.setPrototype(proto1);
        obj2.defineProperty("test", 2, 0);
        assertTrue(fk.isCompatible(obj2));
        assertEquals(2, obj2.getPropertyFast(fk, obj2));
        // Fast key does not work with same shape but different prototype
        var proto2 = (ScriptableObject) cx.newObject(scope);
        proto2.defineProperty("protoProp", 1, 0);
        var obj3 = (ScriptableObject) cx.newObject(scope);
        obj3.setPrototype(proto2);
        obj3.defineProperty("test", 3, 0);
        assertFalse(fk.isCompatible(obj3));
    }

    @Test
    public void testQueryOnePrototypeProperty() {
        var proto1 = (ScriptableObject) cx.newObject(scope);
        proto1.defineProperty("foo", 1, 0);
        var obj = (ScriptableObject) cx.newObject(scope);
        obj.setPrototype(proto1);
        var fk = obj.getFastKey("foo");
        assertNotNull(fk);
        assertTrue(fk.isCompatible(obj));
        assertEquals(1, obj.getPropertyFast(fk, obj));
        // Fast key works with different object with same prototype
        var obj2 = (ScriptableObject) cx.newObject(scope);
        obj2.setPrototype(proto1);
        assertTrue(fk.isCompatible(obj2));
        assertEquals(1, obj2.getPropertyFast(fk, obj2));
        // Fast key does not work with same prototype and different shape
        obj2.defineProperty("one", 1, 0);
        assertFalse(fk.isCompatible(obj2));
    }

    @Test
    public void testModifyOneExistingProperty() {
        var obj = (ScriptableObject) cx.newObject(scope);
        obj.defineProperty("test", 1, 0);
        var fk = obj.getFastWriteKey("test", 0);
        assertNotNull(fk);
        assertEquals(1, obj.getProperty(obj, "test"));
        obj.putPropertyFast(fk, obj, 2, true);
        assertEquals(2, obj.getProperty(obj, "test"));
        // Test that it still works if the object is not extensible
        obj.preventExtensions();
        assertTrue(fk.isCompatible(obj));
        obj.putPropertyFast(fk, obj, 3, true);
        assertEquals(3, obj.getProperty(obj, "test"));
    }

    @Test
    public void testModifyEmptyNewProperty() {
        // Insert a new key totally
        var obj = (ScriptableObject) cx.newObject(scope);
        var fk = obj.getFastWriteKey("test", 0);
        assertNotNull(fk);
        obj.putPropertyFast(fk, obj, 1, true);
        assertEquals(1, obj.get("test", obj));
        // Test that it works with a different empty object
        var obj2 = (ScriptableObject) cx.newObject(scope);
        assertTrue(fk.isCompatible(obj2));
        obj2.putPropertyFast(fk, obj2, 2, true);
        assertEquals(2, obj2.get("test", obj2));
        // Test that it does not work with a different shape
        obj2.defineProperty("test2", 3, 0);
        assertFalse(fk.isCompatible(obj2));
        // And a new object with a different shape
        var obj3 = (ScriptableObject) cx.newObject(scope);
        obj3.defineProperty("test2", 4, 0);
        assertFalse(fk.isCompatible(obj3));
        // But it fails if the object is sealed
        obj.sealObject();
        assertFalse(fk.isCompatible(obj));
        // It also fails if the object is not extensible
        var obj4 = (ScriptableObject) cx.newObject(scope);
        obj4.preventExtensions();
        assertFalse(fk.isCompatible(obj4));
    }

    @Test
    public void testThreeProperties() {
        var obj = (ScriptableObject) cx.newObject(scope);
        obj.defineProperty("test", 1, 0);
        obj.defineProperty("test2", 2, 0);
        obj.defineProperty("test3", 3, 0);
        var fk = obj.getFastKey("test");
        assertNotNull(fk);
        assertEquals(1, obj.getPropertyFast(fk, obj));
        var notFound = obj.getFastKey("notFound");
        assertNull(notFound);
        // Fast key works with same object
        assertTrue(fk.isCompatible(obj));
        // Fast key works with different object with same shape
        var obj2 = (ScriptableObject) cx.newObject(scope);
        obj2.defineProperty("test", 6, 0);
        obj2.defineProperty("test2", 4, 0);
        obj2.defineProperty("test3", 5, 0);
        assertTrue(fk.isCompatible(obj2));
        assertEquals(6, obj2.getPropertyFast(fk, obj2));
        // Fast key does not work if the shape is different
        obj2.defineProperty("test4", 7, 0);
        assertFalse(fk.isCompatible(obj2));
        // Or if an object has same properties in a different order
        var obj3 = (ScriptableObject) cx.newObject(scope);
        obj3.defineProperty("test", 8, 0);
        obj3.defineProperty("test3", 9, 0);
        obj3.defineProperty("test2", 10, 0);
        assertFalse(fk.isCompatible(obj3));
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
        assertEquals(1, obj.getPropertyFast(fk, obj));
        obj.putPropertyFast(fkw, obj, 4, true);
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
        var obj2 = (ScriptableObject) cx.newObject(scope);
        obj2.defineProperty("test", 1, 0);
        obj2.defineProperty("test2", 2, 0);
        obj2.defineProperty("test3", 3, 0);
        assertTrue(fk.isCompatible(obj2));
        obj2.putPropertyFast(fk, obj2, 4, true);
        assertEquals(4, obj2.get("test4", obj2));
    }
}
