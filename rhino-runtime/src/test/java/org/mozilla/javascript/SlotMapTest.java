package org.mozilla.javascript;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SlotMapTest {
    // Random number generator with fixed seed to ensure repeatable tests
    private static final Random rand = new Random(0);

    private final Class<SlotMap> mapClass;
    private Context cx;
    private SlotMap map;

    public SlotMapTest(Class<SlotMap> mapClass) {
        this.mapClass = mapClass;
    }

    @Before
    public void init()
            throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                    InvocationTargetException, NoSuchMethodException, SecurityException {
        cx = Context.enter();
        map = mapClass.getDeclaredConstructor().newInstance();
    }

    @After
    public void close() {
        Context.exit();
    }

    @Parameterized.Parameters
    public static Collection<Object[]> mapTypes() {
        return Arrays.asList(
                new Object[][] {
                    {HashSlotMap.class}, {ThreadSafeHashSlotMap.class}, {PropertyMapSlotMap.class},
                });
    }

    @Test
    public void empty() {
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertNull(map.query(new Slot.Key("notfound")));
        assertNull(map.query(new Slot.Key(123)));
    }

    @Test
    public void crudOneString() {
        assertNull(map.query(new Slot.Key("foo")));
        Slot slot = map.modify(new Slot.Key("foo"), 0);
        assertNotNull(slot);
        slot.value = "Testing";
        assertEquals(1, map.size());
        assertFalse(map.isEmpty());
        slot = map.modify(new Slot.Key("foo"), 0);
        assertEquals(1, map.size());
        assertFalse(map.isEmpty());
        map.remove(new Slot.Key("foo"));
        assertNull(map.query(new Slot.Key("foo")));
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        map.remove(new Slot.Key("foo"));
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    public void crudOneIndex() {
        assertNull(map.query(new Slot.Key(11)));
        Slot slot = map.modify(new Slot.Key(11), 0);
        assertNotNull(slot);
        slot.value = "Testing";
        assertEquals(1, map.size());
        assertFalse(map.isEmpty());
        map.remove(new Slot.Key(11));
        assertNull(map.query(new Slot.Key(11)));
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    public void computeAdd() {
        Slot slot =
                map.compute(
                        new Slot.Key(1),
                        (k, s) -> {
                            assertEquals(new Slot.Key(1), k);
                            assertNull(s);
                            return new Slot(k, 0);
                        });
        assertEquals(new Slot.Key(1), slot.key);
        assertEquals(1, map.size());
        Slot slot2 =
                map.compute(
                        new Slot.Key(1),
                        (k, s) -> {
                            assertEquals(new Slot.Key(1), k);
                            assertSame(slot, s);
                            return s;
                        });
        assertSame(slot2, slot);
        assertEquals(1, map.size());
    }

    @Test
    public void computeRemove() {
        Slot slot = map.modify(new Slot.Key(1), 0);
        assertEquals(1, map.size());
        Slot slot2 =
                map.compute(
                        new Slot.Key(1),
                        (k, s) -> {
                            assertEquals(new Slot.Key(1), k);
                            assertSame(slot, s);
                            return s;
                        });
        assertSame(slot2, slot);
        assertEquals(1, map.size());
        Slot slot3 =
                map.compute(
                        new Slot.Key(1),
                        (k, s) -> {
                            assertEquals(new Slot.Key(1), k);
                            assertSame(slot, s);
                            return null;
                        });
        assertNull(slot3);
        assertEquals(0, map.size());
    }

    // For good testing, should be bigger than max slot size
    private static final int NUM_INDICES = 67;

    @Test
    public void manyKeysAndIndices() {
        for (int i = 0; i < NUM_INDICES; i++) {
            Slot newSlot = map.modify(new Slot.Key(i), 0);
            newSlot.value = i;
        }
        for (String key : KEYS) {
            Slot newSlot = map.modify(new Slot.Key(key), 0);
            newSlot.value = key;
        }
        assertEquals(KEYS.length + NUM_INDICES, map.size());
        assertFalse(map.isEmpty());
        verifyIndicesAndKeys();

        // Randomly replace some stuff
        for (int i = 0; i < 20; i++) {
            int ix = rand.nextInt(NUM_INDICES);
            Slot slot = map.query(new Slot.Key(ix));
            assertNotNull(slot);
        }
        for (int i = 0; i < 20; i++) {
            int ix = rand.nextInt(KEYS.length);
            Slot slot = map.query(new Slot.Key(KEYS[ix]));
            assertNotNull(slot);
        }
        verifyIndicesAndKeys();

        HashSet<Integer> removedIds = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            int ix = rand.nextInt(NUM_INDICES);
            map.remove(new Slot.Key(ix));
            removedIds.add(ix);
        }
        HashSet<String> removedKeys = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            int ix = rand.nextInt(NUM_INDICES);
            map.remove(new Slot.Key(KEYS[ix]));
            removedKeys.add(KEYS[ix]);
        }

        for (int i = 0; i < NUM_INDICES; i++) {
            Slot slot = map.query(new Slot.Key(i));
            if (removedIds.contains(i)) {
                assertNull(slot);
            } else {
                assertNotNull(slot);
                assertEquals(i, slot.value);
            }
        }
        for (String key : KEYS) {
            Slot slot = map.query(new Slot.Key(key));
            if (removedKeys.contains(key)) {
                assertNull(slot);
            } else {
                assertNotNull(slot);
                assertEquals(key, slot.value);
            }
        }
    }

    private void verifyIndicesAndKeys() {
        long lockStamp = 0;
        lockStamp = map.readLock();
        try {
            Iterator<Slot> it = map.iterator();
            for (int i = 0; i < NUM_INDICES; i++) {
                Slot slot = map.query(new Slot.Key(i));
                assertNotNull(slot);
                assertEquals(i, slot.value);
                assertTrue(it.hasNext());
                assertEquals(slot, it.next());
            }
            for (String key : KEYS) {
                Slot slot = map.query(new Slot.Key(key));
                assertNotNull(slot);
                assertEquals(key, slot.value);
                assertTrue(it.hasNext());
                assertEquals(slot, it.next());
            }
            assertFalse(it.hasNext());
        } finally {
            map.unlockRead(lockStamp);
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
