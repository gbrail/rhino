package org.mozilla.javascript;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

import org.mozilla.javascript.Slot.Key;

public class PropertyMapSlotMap implements SlotMap {

    private PropertyMap propertyMap;
    private Slot[] slots;
    private HashSlotMap hashSlots;
    private int numSlots;
    private boolean atCapacity;

    private static final int INITIAL_SLOTS = 8;
    // Max must be a multiple of initial
    private static final int MAX_SLOTS = 64;

    public PropertyMapSlotMap() {
        // Always start with the empty root property map
        propertyMap = Context.getCurrentContext().getRootPropertyMap();
        slots = new Slot[INITIAL_SLOTS];
    }

    @Override
    public boolean isEmpty() {
        return numSlots == 0 && (hashSlots == null || hashSlots.isEmpty());
    }

    @Override
    public int size() {
        return numSlots + (hashSlots == null ? 0 : hashSlots.size());
    }

    @Override
    public boolean has(Slot.Key key) {
        int ix = propertyMap.get(key);
        if (ix >= 0 && ix < slots.length) {
            return slots[ix] != null;
        }
        if (hashSlots != null) {
            return hashSlots.has(key);
        }
        return false;
    }

    @Override
    public Slot query(Slot.Key key) {
        int ix = propertyMap.get(key);
        if (ix >= 0 && ix < slots.length) {
            return slots[ix];
        }
        if (hashSlots != null) {
            return hashSlots.query(key);
        }
        return null;
    }

    @Override
    public CacheableResult<Slot> queryAndGetCacheInfo(Slot.Key key) {
        int ix = propertyMap.get(key);
        if (ix >= 0 && ix < slots.length) {
            return new CacheableResult<>(slots[ix], ix, propertyMap);
        }
        if (hashSlots != null) {
            return new CacheableResult<>(hashSlots.query(key));
        }
        return null;
    }

    @Override
    public Slot queryFromCache(int index) {
        return slots[index];
    }

    /**
     * If there is capacity for index "ix," regardless of whether capacity must be increased, then
     * return true. If we have already reached the maximum number of slots, return false.
     */
    private boolean ensureCapacity(int ix) {
        while (ix >= slots.length) {
            if (slots.length >= MAX_SLOTS) {
                atCapacity = true;
                return false;
            }
            Slot[] oldSlots = slots;
            slots = new Slot[oldSlots.length * 2];
            System.arraycopy(oldSlots, 0, slots, 0, oldSlots.length);
        }
        assert (ix < slots.length);
        return true;
    }

    private Slot createSlotIfAbsent(int ix, Slot.Key key, int attributes) {
        Slot slot = slots[ix];
        if (slot == null) {
            slot = new Slot(key, attributes);
            slots[ix] = slot;
            numSlots++;
        }
        return slot;
    }

    @Override
    public Slot modify(Slot.Key key, int attributes) {
        if (atCapacity) {
            int ix = propertyMap.get(key);
            if (ix >= 0 && ix < slots.length) {
                // Map is full, so use slot only if it fits in the map
                return createSlotIfAbsent(ix, key, attributes);
            }
            // Otherwise, fall back to hash table
            if (hashSlots == null) {
                hashSlots = new HashSlotMap();
            }
            return hashSlots.modify(key, attributes);
        }

        PropertyMap.AddResult r = propertyMap.add(key);
        if (r.hasMap()) {
            // A new property was added, so switch to a new map
            propertyMap = r.getMap();
        }
        int ix = r.getIndex();
        if (ensureCapacity(ix)) {
            // Map is not full, so we may have added a new slot.
            return createSlotIfAbsent(ix, key, attributes);
        }
        // If we get here, we're at capacity so start again
        assert (atCapacity);
        return modify(key, attributes);
    }

    private void addSlot(int ix, Slot newSlot) {
        if (slots[ix] == null) {
            numSlots++;
        }
        slots[ix] = newSlot;
    }

    @Override
    public void add(Slot newSlot) {
        if (atCapacity) {
            int ix = propertyMap.get(newSlot.key);
            if (ix >= 0 && ix < slots.length) {
                addSlot(ix, newSlot);
                return;
            }
            if (hashSlots == null) {
                hashSlots = new HashSlotMap();
            }
            hashSlots.add(newSlot);
            return;
        }

        PropertyMap.AddResult r = propertyMap.add(newSlot.key);
        if (r.hasMap()) {
            propertyMap = r.getMap();
        }
        int ix = r.getIndex();
        if (ensureCapacity(ix)) {
            addSlot(ix, newSlot);
            return;
        }
        // If we get here, we're at capacity so start again
        assert (atCapacity);
        add(newSlot);
    }

    private <S extends Slot> S computeSlot(int ix, Slot.Key key, BiFunction<Slot.Key, Slot, S> f) {
        Slot existing = slots[ix];
        S newSlot = f.apply(key, existing);
        slots[ix] = newSlot;
        if (existing == null && newSlot != null) {
            numSlots++;
        } else if (existing != null && newSlot == null) {
            numSlots--;
        }
        return newSlot;
    }

    @Override
    public <S extends Slot> S compute(Slot.Key key, BiFunction<Slot.Key, Slot, S> f) {
        if (atCapacity) {
            int ix = propertyMap.get(key);
            if (ix >= 0 && ix < slots.length) {
                return computeSlot(ix, key, f);
            }
            // Otherwise, fall back to hash table
            if (hashSlots == null) {
                hashSlots = new HashSlotMap();
            }
            return hashSlots.compute(key, f);
        }

        PropertyMap.AddResult r = propertyMap.add(key);
        if (r.hasMap()) {
            propertyMap = r.getMap();
        }
        int ix = r.getIndex();
        if (ensureCapacity(ix)) {
            return computeSlot(ix, key, f);
        }
        // If we get here, we're at capacity so start again
        assert (atCapacity);
        return compute(key, f);
    }

    @Override
    public void remove(Slot.Key key) {
        int ix = propertyMap.get(key);
        if ((ix >= 0) && (ix < slots.length)) {
            // We don't change the property map when we remove -- just
            // null out the slot.
            Slot slot = slots[ix];
            if (slot != null) {
                if (!slot.checkIsPermanent()) {
                    numSlots--;
                    slots[ix] = null;
                }
            }
        } else if (hashSlots != null) {
            hashSlots.remove(key);
        }
    }

    @Override
    public Iterator<Slot> iterator() {
        return new Itr();
    }

    @Override
    public long readLock() {
        return 0;
    }

    @Override
    public void unlockRead(long stamp) {}

    public final class Itr implements Iterator<Slot> {
        private int pos;
        private Iterator<Slot> hashIterator;

        Itr() {
            pos = 0;
            hashIterator = hashSlots == null ? null : hashSlots.iterator();
        }

        @Override
        public boolean hasNext() {
            while (pos < slots.length && slots[pos] == null) {
                pos++;
            }
            if (pos < slots.length) {
                return true;
            }
            if (hashIterator != null) {
                return hashIterator.hasNext();
            }
            return false;
        }

        @Override
        public Slot next() {
            while (pos < slots.length && slots[pos] == null) {
                pos++;
            }
            if (pos < slots.length) {
                Slot slot = slots[pos];
                pos++;
                return slot;
            }
            if (hashIterator != null) {
                return hashIterator.next();
            }
            throw new NoSuchElementException();
        }
    }

    @Override
    public PropertyMap getPropertyMap() {
        return propertyMap;
    }
}
