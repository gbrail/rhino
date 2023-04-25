package org.mozilla.javascript;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This class implements the SlotMap using a PropertyMap. That makse it possible
 * to optimize with a
 * fast past in generated code.
 */
public class IndexedSlotMap implements SlotMap {
    /** This type of slot map has a fixed maximum capacity. */
    static final int CAPACITY = 10;

    private final Slot[] slots = new Slot[10];
    private int size = 0;
    private PropertyMap propertyMap = PropertyMap.ROOT;

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size != 0;
    }

    @Override
    public boolean maxCapacity() {
        return size == CAPACITY;
    }

    /** On a query, the property map leads us to the right index. */
    @Override
    public Slot query(Object k, int index) {
        Object key = makeKey(k, index);
        int ix = propertyMap.find(key);
        if (ix < 0) {
            return null;
        }
        return slots[ix];
    }

    /** When we modify, we switch to a new property map. */
    @Override
    public Slot modify(Object k, int index, int attributes) {
        // First, return the existing slot.
        Object key = makeKey(k, index);
        int ix = propertyMap.find(key);
        if (ix >= 0) {
            return slots[ix];
        }
        // If not there, switch to a new property map and append.
        int indexOrHash = (k != null ? k.hashCode() : index);
        Slot newSlot = new Slot(k, indexOrHash, attributes);
        insertNewSlot(key, newSlot);
        return newSlot;
    }

    @Override
    public void replace(Slot oldSlot, Slot newSlot) {
        for (int ix = 0; ix < size; ix++) {
            if (slots[ix] == oldSlot) {
                slots[ix] = newSlot;
                return;
            }
        }
    }

    @Override
    public void add(Slot newSlot) {
        insertNewSlot(makeKey(newSlot.name, newSlot.indexOrHash), newSlot);
    }

    @Override
    public void remove(Object k, int index) {
        Object key = makeKey(k, index);
        // TODO this is incorrect if keys are not removed in right order.
        // Need to revert to new map type when this happens.
        propertyMap = propertyMap.remove(key);
        assert (propertyMap != null);
        size--;
    }

    @Override
    public Iterator<Slot> iterator() {
        return new Iter();
    }

    private void insertNewSlot(Object key, Slot newSlot) {
        propertyMap = propertyMap.add(key);
        slots[size] = newSlot;
        size++;
    }

    private Object makeKey(Object key, int index) {
        if (key == null) {
            return Integer.valueOf(index);
        }
        return key;
    }

    private final class Iter implements Iterator<Slot> {
        private int ix = 0;

        @Override
        public boolean hasNext() {
            return ix < size;
        }

        @Override
        public Slot next() {
            if (ix == size) {
                throw new NoSuchElementException();
            }
            Slot ret = slots[ix];
            ix++;
            return ret;
        }
    }
}
