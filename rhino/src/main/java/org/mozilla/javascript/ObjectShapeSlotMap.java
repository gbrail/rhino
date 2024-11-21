package org.mozilla.javascript;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public class ObjectShapeSlotMap implements SlotMap {
    // The map that represents the current set of properties
    ObjectShape shape = ObjectShape.EMPTY;
    // All the slots in insertion order. Deleted slots are replaced with nulls.
    private Slot[] orderedSlots;
    // The length of the ordered slots array, including nulls
    private int orderedLength;
    // The number of slots in the map
    private int count;
    // Keep track of deletes because we use a sparse array
    private int deleteCount;

    // Beyond this size, we expect that this map will be replaced with
    // a more collision-resistant HashSlotMap.
    static final int SIZE_LIMIT = 100;
    // After this many deletes, we should also be replaced
    private static final int DELETE_LIMIT = 16;

    private static final int INITIAL_LIST_SIZE = 8;

    @Override
    public int size() {
        return count;
    }

    @Override
    public boolean isEmpty() {
        return count == 0;
    }

    @Override
    public boolean isLimitReached() {
        return count >= SIZE_LIMIT || deleteCount >= DELETE_LIMIT;
    }

    @Override
    public Iterator<Slot> iterator() {
        return new ObjectShapeSlotMap.Iter();
    }

    @Override
    public Slot query(Object key, int index) {
        int ix = shape.find(makeKey(key, index));
        if (ix < 0) {
            return null;
        }
        return orderedSlots[ix];
    }

    @Override
    public int getFastQueryIndex(Object key, int index) {
        return shape.find(makeKey(key, index));
    }

    @Override
    public boolean testFastQuery(SlotMap map, int index) {
        if (map instanceof ObjectShapeSlotMap) {
            return Objects.equals(shape, ((ObjectShapeSlotMap) map).shape);
        }
        return false;
    }

    @Override
    public Slot queryFast(int index) {
        return orderedSlots[index];
    }

    @Override
    public Slot modify(Object key, int index, int attributes) {
        Object k = makeKey(key, index);
        int ix = shape.find(k);
        if (ix >= 0) {
            Slot slot = orderedSlots[ix];
            if (slot == null) {
                // It may have been removed
                slot = new Slot(key, index, attributes);
                slot.orderedPos = ix;
                orderedSlots[ix] = slot;
                count++;
            }
            return slot;
        }

        Slot newSlot = new Slot(key, index, attributes);
        insertNewSlot(newSlot);
        shape = shape.add(k);
        return newSlot;
    }

    @Override
    public <S extends Slot> S compute(Object key, int index, SlotComputer<S> c) {
        Object k = makeKey(key, index);
        int ix = shape.find(k);
        if (ix >= 0) {
            Slot slot = orderedSlots[ix];
            S newSlot = c.compute(key, index, slot);
            if (newSlot == null) {
                // Need to remove slot. Don't change shape though.
                if (orderedSlots[ix] != null) {
                    orderedSlots[ix] = null;
                    count--;
                    deleteCount++;
                }
            } else if (!Objects.equals(slot, newSlot)) {
                // Replace slot in ordered index
                if (slot == null) {
                    // Slot may have been removed previously
                    count++;
                }
                orderedSlots[ix] = newSlot;
                newSlot.orderedPos = slot.orderedPos;
            }
            return newSlot;
        }

        // If we get here, we know we are potentially adding a new slot
        S newSlot = c.compute(key, index, null);
        if (newSlot != null) {
            insertNewSlot(newSlot);
            shape = shape.add(k);
        }
        return newSlot;
    }

    @Override
    public void add(Slot newSlot) {
        insertNewSlot(newSlot);
        shape = shape.add(makeKey(newSlot.name, newSlot.indexOrHash));
    }

    private void insertNewSlot(Slot newSlot) {
        if (count == 0) {
            orderedSlots = new Slot[INITIAL_LIST_SIZE];
        }
        count++;
        if (orderedLength == orderedSlots.length) {
            Slot[] newOrderedSlots = new Slot[orderedSlots.length * 2];
            System.arraycopy(orderedSlots, 0, newOrderedSlots, 0, orderedSlots.length);
            orderedSlots = newOrderedSlots;
        }
        orderedSlots[orderedLength] = newSlot;
        newSlot.orderedPos = orderedLength;
        orderedLength++;
    }

    private static Object makeKey(Object key, int index) {
        return key == null ? Integer.valueOf(index) : key;
    }

    private final class Iter implements Iterator<Slot> {

        private int pos;

        @Override
        public boolean hasNext() {
            while (pos < orderedLength && orderedSlots[pos] == null) {
                // Skip deleted slots
                pos++;
            }
            return pos < orderedLength;
        }

        @Override
        public Slot next() {
            while (pos < orderedLength && orderedSlots[pos] == null) {
                // Skip deleted slots in case someone doesn't call hasNext
                pos++;
            }
            if (pos >= orderedLength) {
                throw new NoSuchElementException();
            }
            return orderedSlots[pos++];
        }
    }
}
