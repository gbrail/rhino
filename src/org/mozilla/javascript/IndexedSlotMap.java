package org.mozilla.javascript;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.LongAccumulator;

/**
 * This class implements the SlotMap using a PropertyMap for the first 10 keys, and then uses a
 * HashMap for additional keys.
 */
public class IndexedSlotMap implements SlotMap {
    /** The number of slots to index using the property map. */
    static final int FAST_SLOT_SIZE = 30;

    private final Slot[] fastSlots = new Slot[FAST_SLOT_SIZE];
    private int fastSize = 0;
    private LinkedHashMap<Object, Slot> slowSlots = null;
    private PropertyMap propertyMap = PropertyMap.ROOT;
    private static final boolean accumulateStats;

    private static final LongAccumulator mapCount = new LongAccumulator(Long::sum, 0);
    private static final LongAccumulator mapsRemovedCount = new LongAccumulator(Long::sum, 0);
    private static final LongAccumulator mapsGrownCount = new LongAccumulator(Long::sum, 0);

    static {
        String propVal = System.getProperty("RhinoSlotStats");
        accumulateStats = propVal != null;
    }

    public IndexedSlotMap() {
        if (accumulateStats) {
            mapCount.accumulate(1);
        }
    }

    @Override
    public int size() {
        return fastSize + (slowSlots == null ? 0 : slowSlots.size());
    }

    @Override
    public boolean isEmpty() {
        return fastSize == 0 && (slowSlots == null || slowSlots.isEmpty());
    }

    /**
     * On a query, the property map may lead us to the right index, and if not, we look in the hash
     * map.
     */
    @Override
    public Slot query(Object k, int index) {
        Object key = makeKey(k, index);
        if (fastSize > 0) {
            int ix = propertyMap.find(key);
            if (ix >= 0) {
                assert (ix < fastSize);
                return fastSlots[ix];
            }
        }
        if (slowSlots == null) {
            return null;
        }
        return slowSlots.get(key);
    }

    @Override
    public FastKey getFastKey(Object k, int index) {
        Object key = makeKey(k, index);
        if (fastSize > 0) {
            int ix = propertyMap.find(key);
            if (ix >= 0) {
                assert (ix < fastSize);
                return new FastKey(propertyMap, ix);
            }
        }
        return null;
    }

    @Override
    public Slot queryFast(FastKey key) {
        if (Objects.equals(key.map, propertyMap) && (key.index < fastSize)) {
            return fastSlots[key.index];
        }
        return SlotMap.NOT_A_FAST_PROPERTY;
    }

    /** When we modify, we switch to a new property map. */
    @Override
    public Slot modify(Object k, int index, int attributes) {
        Object key = makeKey(k, index);
        if (fastSize > 0) {
            int ix = propertyMap.find(key);
            if (ix >= 0) {
                assert (ix < fastSize);
                return fastSlots[ix];
            }
        }
        if (slowSlots != null) {
            Slot found = slowSlots.get(key);
            if (found != null) {
                return found;
            }
        }
        // If not there, switch to a new property map and append.
        int indexOrHash = (k != null ? k.hashCode() : index);
        Slot newSlot = new Slot(k, indexOrHash, attributes);
        insertNewSlot(key, newSlot);
        return newSlot;
    }

    @Override
    public void replace(Slot oldSlot, Slot newSlot) {
        Object key = makeKey(oldSlot.name, oldSlot.indexOrHash);
        if (fastSize > 0) {
            int ix = propertyMap.find(key);
            if (ix >= 0) {
                assert (ix < fastSize);
                fastSlots[ix] = newSlot;
                return;
            }
        }
        if (slowSlots != null) {
            slowSlots.put(key, newSlot);
        }
    }

    @Override
    public void add(Slot newSlot) {
        insertNewSlot(makeKey(newSlot.name, newSlot.indexOrHash), newSlot);
    }

    @Override
    public void remove(Object k, int index) {
        // Fast slots are incompatible with removing stuff.
        // Switch entirely to slow slots in this case.
        // TODO: We can optimize this if removing the last property in the map
        if (slowSlots == null) {
            slowSlots = new LinkedHashMap<>();
        }
        if (fastSize > 0) {
            if (accumulateStats) {
                mapsRemovedCount.accumulate(1);
            }
            // Need to re-build the whole map so that insertion order is preserved.
            LinkedHashMap<Object, Slot> newSlots = new LinkedHashMap<>();
            for (int i = 0; i < fastSize; i++) {
                Slot s = fastSlots[i];
                Object key = makeKey(s.name, s.indexOrHash);
                newSlots.put(key, s);
            }
            fastSize = 0;
            propertyMap = null;
            newSlots.putAll(slowSlots);
            slowSlots = newSlots;
        }

        // Now do the actual removal
        Object key = makeKey(k, index);
        Slot slot = slowSlots.get(key);
        if (slot != null) {
            // non-configurable
            if ((slot.getAttributes() & ScriptableObject.PERMANENT) != 0) {
                Context cx = Context.getContext();
                if (cx.isStrictMode()) {
                    throw ScriptRuntime.typeErrorById(
                            "msg.delete.property.with.configurable.false", key);
                }
                return;
            }
            slowSlots.remove(key);
        }
    }

    @Override
    public Iterator<Slot> iterator() {
        return new Iter();
    }

    private void insertNewSlot(Object key, Slot newSlot) {
        if (propertyMap != null && fastSize < FAST_SLOT_SIZE) {
            propertyMap = propertyMap.add(key);
            fastSlots[fastSize] = newSlot;
            fastSize++;
            assert (fastSize == propertyMap.getLevel() + 1);
        } else {
            if (slowSlots == null) {
                if (accumulateStats) {
                    mapsGrownCount.accumulate(1);
                }
                slowSlots = new LinkedHashMap<>();
            }
            slowSlots.put(key, newSlot);
        }
    }

    private Object makeKey(Object key, int index) {
        if (key == null) {
            return index;
        }
        return key;
    }

    private final class Iter implements Iterator<Slot> {
        private Iterator<Slot> iter = null;
        private int fastIx = 0;
        private boolean done;

        Iter() {
            done = (fastSize == 0) && (slowSlots == null || slowSlots.isEmpty());
        }

        @Override
        public boolean hasNext() {
            return !done;
        }

        @Override
        public Slot next() {
            if (fastIx < fastSize) {
                Slot s = fastSlots[fastIx];
                fastIx++;
                if (fastIx == fastSize && slowSlots == null) {
                    done = true;
                }
                return s;
            }
            if (iter == null) {
                if (slowSlots == null) {
                    throw new NoSuchElementException();
                }
                iter = slowSlots.values().iterator();
            }
            Slot s = iter.next();
            if (!iter.hasNext()) {
                done = true;
            }
            return s;
        }
    }

    public static void printStats() {
        if (accumulateStats) {
            System.out.println("Indexed slot maps created:    " + mapCount.get());
            System.out.println("De-optimized due to removals: " + mapsRemovedCount.get());
            System.out.println("Grown past initial size:      " + mapsGrownCount.get());
        }
    }
}
