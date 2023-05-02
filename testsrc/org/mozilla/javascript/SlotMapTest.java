package org.mozilla.javascript;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SlotMapTest {
    // Random number generator with fixed seed to ensure repeatable tests
    private static final Random rand = new Random(0);

    private final SlotMap map;

    public SlotMapTest(Class<SlotMap> mapClass)
            throws IllegalAccessException, InstantiationException, NoSuchMethodException,
                    InvocationTargetException {
        this.map = mapClass.getDeclaredConstructor().newInstance();
    }

    @Parameterized.Parameters
    public static Collection<Object[]> mapTypes() {
        return Arrays.asList(
                new Object[][] {
                    {EmbeddedSlotMap.class},
                    {HashSlotMap.class},
                    {SlotMapContainer.class},
                    {ThreadSafeSlotMapContainer.class},
                    {IndexedSlotMap.class},
                });
    }

    @Test
    public void empty() {
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertNull(map.query("notfound", 0));
        assertNull(map.query(null, 123));

        long lockStamp = 0;
        if (map instanceof SlotMapContainer) {
            lockStamp = ((SlotMapContainer) map).readLock();
        }
        try {
            assertFalse(map.iterator().hasNext());
        } finally {
            if (map instanceof SlotMapContainer) {
                ((SlotMapContainer) map).unlockRead(lockStamp);
            }
        }
    }

    @Test
    public void crudOneString() {
        assertNull(map.query("foo", 0));
        Slot slot = map.modify("foo", 0, 0);
        assertNotNull(slot);
        slot.value = "Testing";
        assertEquals(1, map.size());
        assertFalse(map.isEmpty());
        Slot newSlot = new Slot(slot);
        map.replace(slot, newSlot);
        Slot foundNewSlot = map.query("foo", 0);
        assertEquals("Testing", foundNewSlot.value);
        assertSame(foundNewSlot, newSlot);

        long lockStamp = 0;
        if (map instanceof SlotMapContainer) {
            lockStamp = ((SlotMapContainer) map).readLock();
        }
        try {
            Iterator<Slot> it = map.iterator();
            assertTrue(it.hasNext());
            Slot i1 = it.next();
            assertEquals(i1.name, "foo");
            assertFalse(it.hasNext());
            assertThrows(NoSuchElementException.class, () -> it.next());
        } finally {
            if (map instanceof SlotMapContainer) {
                ((SlotMapContainer) map).unlockRead(lockStamp);
            }
        }
        map.remove("foo", 0);
        assertNull(map.query("foo", 0));
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());

        if (map instanceof SlotMapContainer) {
            lockStamp = ((SlotMapContainer) map).readLock();
        }
        try {
            assertFalse(map.iterator().hasNext());
        } finally {
            if (map instanceof SlotMapContainer) {
                ((SlotMapContainer) map).unlockRead(lockStamp);
            }
        }
    }

    @Test
    public void crudOneIndex() {
        assertNull(map.query(null, 11));
        Slot slot = map.modify(null, 11, 0);
        assertNotNull(slot);
        slot.value = "Testing";
        assertEquals(1, map.size());
        assertFalse(map.isEmpty());
        Slot newSlot = new Slot(slot);
        map.replace(slot, newSlot);
        Slot foundNewSlot = map.query(null, 11);
        assertEquals("Testing", foundNewSlot.value);
        assertSame(foundNewSlot, newSlot);
        map.remove(null, 11);
        assertNull(map.query(null, 11));
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    private static final int NUM_INDICES = 67;

    @Test
    public void testManyKeysAndIndices() {
        HashSet<Integer> indices = new HashSet<>();
        for (int i = 0; i < NUM_INDICES; i++) {
            Slot newSlot = map.modify(null, i, 0);
            newSlot.value = i;
            indices.add(i);
        }
        HashSet<String> keys = new HashSet<>();
        for (String key : KEYS) {
            Slot newSlot = map.modify(key, 0, 0);
            newSlot.value = key;
            keys.add(key);
        }
        assertEquals(KEYS.length + NUM_INDICES, map.size());
        assertFalse(map.isEmpty());
        verifyIndicesAndKeys(indices, keys);

        // Randomly replace some stuff
        for (int i = 0; i < 20; i++) {
            int ix = rand.nextInt(NUM_INDICES);
            Slot slot = map.query(null, ix);
            assertNotNull(slot);
            Slot newSlot = new Slot(slot);
            map.replace(slot, newSlot);
        }
        for (int i = 0; i < 20; i++) {
            int ix = rand.nextInt(KEYS.length);
            Slot slot = map.query(KEYS[ix], 0);
            assertNotNull(slot);
            Slot newSlot = new Slot(slot);
            map.replace(slot, newSlot);
        }
        verifyIndicesAndKeys(indices, keys);

        // Randomly remove some stuff
        for (int i = 0; i < 20; ) {
            int ix = rand.nextInt(NUM_INDICES);
            if (indices.remove(ix)) {
                map.remove(null, ix);
                i++;
            }
        }
        for (int i = 0; i < 20; ) {
            int ix = rand.nextInt(KEYS.length);
            if (keys.remove(KEYS[ix])) {
                map.remove(KEYS[ix], ix);
                i++;
            }
        }
        verifyIndicesAndKeys(indices, keys);
    }

    private void verifyIndicesAndKeys(HashSet<Integer> indices, HashSet<String> keys) {
        long lockStamp = 0;
        if (map instanceof SlotMapContainer) {
            lockStamp = ((SlotMapContainer) map).readLock();
        }
        try {
            // Verify that all indices and keys are present
            for (int i : indices) {
                Slot slot = map.query(null, i);
                assertNotNull(slot);
                assertEquals(i, slot.value);
            }
            for (String k : keys) {
                Slot slot = map.query(k, 0);
                assertNotNull(slot);
                assertEquals(k, slot.value);
            }

            // Compare iterator to expected indices and keys
            HashSet<Integer> rIndices = new HashSet<>(indices);
            HashSet<String> rKeys = new HashSet<>(keys);
            for (Slot s : map) {
                // If we fail here, the iterator returned something it shouldn't
                if (s.name == null) {
                    assertTrue(rIndices.remove(s.indexOrHash));
                } else {
                    assertTrue(rKeys.remove(s.name));
                }
            }
            // If we fail here, the iterator didn't return everything it should
            assertTrue(rIndices.isEmpty());
            assertTrue(rKeys.isEmpty());
        } finally {
            if (map instanceof SlotMapContainer) {
                ((SlotMapContainer) map).unlockRead(lockStamp);
            }
        }
    }

    // These keys come from the hash collision test and may help ensure that we have a few
    // collisions for proper testing of the embedded slot map.
    private static final String[] KEYS = {
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaAaAaAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaAaAaAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaAaAaBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaAaAaBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaAaBBAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaAaBBAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaAaBBBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaAaBBBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaBBAaAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaBBAaAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaBBAaBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaBBAaBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaBBBBAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaBBBBAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaBBBBBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaAaBBBBBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBAaAaAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBAaAaAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBAaAaBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBAaAaBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBAaBBAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBAaBBAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBAaBBBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBAaBBBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBBBAaAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBBBAaAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBBBAaBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBBBAaBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBBBBBAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBBBBBAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBBBBBBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaAaBBBBBBBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaAaAaAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaAaAaAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaAaAaBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaAaAaBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaAaBBAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaAaBBAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaAaBBBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaAaBBBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaBBAaAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaBBAaAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaBBAaBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaBBAaBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaBBBBAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaBBBBAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaBBBBBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBAaBBBBBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBAaAaAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBAaAaAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBAaAaBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBAaAaBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBAaBBAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBAaBBAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBAaBBBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBAaBBBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBBBAaAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBBBAaAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBBBAaBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBBBAaBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBBBBBAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBBBBBAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBBBBBBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaAaBBBBBBBBBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaAaAaAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaAaAaAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaAaAaBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaAaAaBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaAaBBAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaAaBBAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaAaBBBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaAaBBBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaBBAaAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaBBAaAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaBBAaBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaBBAaBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaBBBBAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaBBBBAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaBBBBBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaAaBBBBBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBAaAaAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBAaAaAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBAaAaBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBAaAaBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBAaBBAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBAaBBAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBAaBBBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBAaBBBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBBBAaAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBBBAaAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBBBAaBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBBBAaBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBBBBBAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBBBBBAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBBBBBBBAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBAaBBBBBBBBBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBBBAaAaAaAaAa",
        "AaAaAaAaAaAaAaAaAaAaAaBBBBAaAaAaAaBB",
        "AaAaAaAaAaAaAaAaAaAaAaBBBBAaAaAaBBAa",
    };
}
