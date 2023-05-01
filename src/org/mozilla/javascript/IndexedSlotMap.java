package org.mozilla.javascript;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * This class implements the SlotMap using a PropertyMap for the first 10 keys, and then uses a
 * HashMap for additional keys.
 */
public class IndexedSlotMap implements SlotMap {
    /** The number of slots to index using the property map. */
    static final int FAST_SLOT_SIZE = 10;

    private ArrayList<Slot> fastSlots = new ArrayList<>();
    private LinkedHashMap<Object, Slot> slowSlots = null;
    private PropertyMap propertyMap = PropertyMap.ROOT;

    @Override
    public int size() {
        return (fastSlots == null ? 0 : fastSlots.size())
                + (slowSlots == null ? 0 : slowSlots.size());
    }

    @Override
    public boolean isEmpty() {
        return (fastSlots == null || fastSlots.isEmpty())
                && (slowSlots == null || slowSlots.isEmpty());
    }

    /**
     * On a query, the property map may lead us to the right index, and if not, we look in the hash
     * map.
     */
    @Override
    public Slot query(Object k, int index) {
        Object key = makeKey(k, index);
        if (fastSlots != null) {
            int ix = propertyMap.find(key);
            if (ix >= 0) {
                assert (ix < fastSlots.size());
                return fastSlots.get(ix);
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
        if (fastSlots != null) {
            int ix = propertyMap.find(key);
            if (ix >= 0) {
                assert (ix < fastSlots.size());
                return new FastKey(propertyMap, ix);
            }
        }
        return null;
    }

    @Override
    public Slot queryFast(FastKey key) {
        if (Objects.equals(key.map, propertyMap) && fastSlots != null) {
            return fastSlots.get(key.index);
        }
        return SlotMap.NOT_A_FAST_PROPERTY;
    }

    /** When we modify, we switch to a new property map. */
    @Override
    public Slot modify(Object k, int index, int attributes) {
        Object key = makeKey(k, index);
        if (fastSlots != null) {
            int ix = propertyMap.find(key);
            if (ix >= 0) {
                assert (ix < fastSlots.size());
                return fastSlots.get(ix);
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
        if (fastSlots != null) {
            int ix = propertyMap.find(key);
            if (ix >= 0) {
                assert (ix < fastSlots.size());
                fastSlots.set(ix, newSlot);
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
        if (fastSlots != null) {
            // Need to re-build the whole map so that insertion order is preserved.
            LinkedHashMap<Object, Slot> newSlots = new LinkedHashMap<>();
            for (Slot s : fastSlots) {
                Object key = makeKey(s.name, s.indexOrHash);
                newSlots.put(key, s);
            }
            fastSlots = null;
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
        if (fastSlots != null && fastSlots.size() < FAST_SLOT_SIZE) {
            propertyMap = propertyMap.add(key);
            fastSlots.add(newSlot);
            assert (fastSlots.size() == propertyMap.getLevel() + 1);
        } else {
            if (slowSlots == null) {
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
        private Iterator<Slot> iter;
        private boolean fastDone;

        Iter() {
            if (fastSlots != null) {
                iter = fastSlots.iterator();
                fastDone = false;
            } else if (slowSlots != null) {
                iter = slowSlots.values().iterator();
                fastDone = true;
            } else {
                iter = null;
            }
        }

        @Override
        public boolean hasNext() {
            return iter != null && iter.hasNext();
        }

        @Override
        public Slot next() {
            if (iter == null) {
                throw new NoSuchElementException();
            }
            Slot s = iter.next();
            if (!iter.hasNext()) {
                if (!fastDone) {
                    iter = slowSlots == null ? null : slowSlots.values().iterator();
                    fastDone = true;
                } else {
                    iter = null;
                }
            }
            return s;
        }
    }
}
