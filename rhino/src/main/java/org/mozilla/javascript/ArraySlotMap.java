package org.mozilla.javascript;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * This is a slot map implementation that keeps the slots in an array. This way, we can take
 * shortcuts to access slots by index rather than by hash lookup. There is still a hash table, but
 * instead of a linked list it uses an array. If too many objects are deleted, then this slot map
 * should be replaced with a HashSlotMap.
 */
public class ArraySlotMap implements SlotMap {
    // The hash table of slots
    private Slot[] hashSlots;
    // All the slots in insertion order. Deleted slots are replaced with nulls.
    private Slot[] orderedSlots;
    // The length of the ordered slots array, including nulls
    private int orderedLength;
    // The number of slots in the map
    private int count;
    // Keep track of deletes because we use a sparse array
    private int deleteCount;
    // The hash, which takes into account the properties and their order
    private long hashCode;

    // Beyond this size, we expect that this map will be replaced with
    // a more collision-resistant HashSlotMap.
    static final int SIZE_LIMIT = 2000;
    // After this many deletes, we should also be replaced
    private static final int DELETE_LIMIT = 16;
    // The number of properties to count in the hash
    private static final int MAX_HASHED_PROPERTIES = 16;

    private static final int INITIAL_MAP_SIZE = 4;
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
        return new Iter();
    }

    @Override
    public Slot query(Object key, int index) {
        if (hashSlots == null) {
            return null;
        }

        int indexOrHash = (key != null ? key.hashCode() : index);
        int slotIndex = getSlotIndex(hashSlots.length, indexOrHash);
        for (Slot slot = hashSlots[slotIndex]; slot != null; slot = slot.next) {
            if (indexOrHash == slot.indexOrHash && Objects.equals(slot.name, key)) {
                return slot;
            }
        }
        return null;
    }

    @Override
    public int getFastQueryIndex(Object key, int index) {
        if (hashSlots == null) {
            return -1;
        }

        int indexOrHash = (key != null ? key.hashCode() : index);
        int slotIndex = getSlotIndex(hashSlots.length, indexOrHash);
        for (Slot slot = hashSlots[slotIndex]; slot != null; slot = slot.next) {
            if (indexOrHash == slot.indexOrHash && Objects.equals(slot.name, key)) {
                return slot.orderedPos;
            }
        }
        return -1;
    }

    @Override
    public Slot testFastQuery(SlotMap map, int index) {
        if (Objects.equals(this, map)
                || (map instanceof ArraySlotMap
                        && index <= MAX_HASHED_PROPERTIES
                        && hashCode >= 0
                        && ((ArraySlotMap) map).hashCode == hashCode)) {
            return orderedSlots[index];
        }
        return null;
    }

    @Override
    public Slot queryFast(int index) {
        return orderedSlots[index];
    }

    @Override
    public Slot modify(Object key, int index, int attributes) {
        int indexOrHash = (key != null ? key.hashCode() : index);
        Slot slot;

        if (hashSlots != null) {
            final int slotIndex = getSlotIndex(hashSlots.length, indexOrHash);
            for (slot = hashSlots[slotIndex]; slot != null; slot = slot.next) {
                if (indexOrHash == slot.indexOrHash && Objects.equals(slot.name, key)) {
                    break;
                }
            }
            if (slot != null) {
                return slot;
            }
        }

        // A new slot has to be inserted.
        Slot newSlot = new Slot(key, index, attributes);
        createNewSlot(newSlot);
        return newSlot;
    }

    @Override
    public <S extends Slot> S compute(Object key, int index, SlotComputer<S> c) {
        int indexOrHash = (key != null ? key.hashCode() : index);

        if (hashSlots != null) {
            Slot slot;
            int slotIndex = getSlotIndex(hashSlots.length, indexOrHash);
            Slot prev = hashSlots[slotIndex];
            for (slot = prev; slot != null; slot = slot.next) {
                if (indexOrHash == slot.indexOrHash && Objects.equals(slot.name, key)) {
                    break;
                }
                prev = slot;
            }
            if (slot != null) {
                // Modify or remove existing slot
                S newSlot = c.compute(key, index, slot);
                if (newSlot == null) {
                    // Need to delete this slot actually
                    removeSlot(slot, prev, slotIndex, key);
                } else if (!Objects.equals(slot, newSlot)) {
                    // Replace slot in hash table
                    if (prev == slot) {
                        hashSlots[slotIndex] = newSlot;
                    } else {
                        prev.next = newSlot;
                    }
                    newSlot.next = slot.next;
                    // Replace slot in ordered index
                    orderedSlots[slot.orderedPos] = newSlot;
                    newSlot.orderedPos = slot.orderedPos;
                }
                return newSlot;
            }
        }

        // If we get here, we know we are potentially adding a new slot
        S newSlot = c.compute(key, index, null);
        if (newSlot != null) {
            createNewSlot(newSlot);
        }
        return newSlot;
    }

    @Override
    public void add(Slot newSlot) {
        if (hashSlots == null) {
            hashSlots = new Slot[INITIAL_MAP_SIZE];
        }
        insertNewSlot(newSlot);
    }

    private void createNewSlot(Slot newSlot) {
        if (count == 0) {
            // Always throw away old slots if any on empty insert.
            hashSlots = new Slot[INITIAL_MAP_SIZE];
        }

        // Check if the table is not too full before inserting.
        if (4 * (count + 1) > 3 * hashSlots.length) {
            // table size must be a power of 2 -- always grow by x2!
            Slot[] newSlots = new Slot[hashSlots.length * 2];
            copyTable(hashSlots, newSlots);
            hashSlots = newSlots;
        }

        insertNewSlot(newSlot);
    }

    private void insertNewSlot(Slot newSlot) {
        if (orderedSlots == null) {
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
        if (hashCode >= 0 && orderedLength <= MAX_HASHED_PROPERTIES) {
            hashCode += ((long) orderedLength << 32L) | (long) newSlot.indexOrHash;
        }
        orderedLength++;
        addKnownAbsentSlot(hashSlots, newSlot);
    }

    private void removeSlot(Slot slot, Slot prev, int ix, Object key) {
        count--;
        deleteCount++;
        // remove slot from hash table
        if (prev == slot) {
            hashSlots[ix] = slot.next;
        } else {
            prev.next = slot.next;
        }
        // Since removes are rare, we just null out the slot in the ordered array.
        // We use other mechanisms to switch to a hash slot map if there are
        // a lot of removals (i.e. if someone is using an object as a map).
        orderedSlots[slot.orderedPos] = null;
        // Don't re-calculate hash on rare case of delete -- invalidate it
        hashCode = -1L;
    }

    private static void copyTable(Slot[] oldSlots, Slot[] newSlots) {
        for (Slot slot : oldSlots) {
            while (slot != null) {
                Slot nextSlot = slot.next;
                addKnownAbsentSlot(newSlots, slot);
                slot = nextSlot;
            }
        }
    }

    /**
     * Add slot with keys that are known to absent from the table. This is an optimization to use
     * when inserting into empty table, after table growth or during deserialization.
     */
    private static void addKnownAbsentSlot(Slot[] addSlots, Slot slot) {
        int insertPos = getSlotIndex(addSlots.length, slot.indexOrHash);
        slot.next = addSlots[insertPos];
        addSlots[insertPos] = slot;
    }

    private static int getSlotIndex(int tableSize, int indexOrHash) {
        // This is a Java trick to efficiently "mod" the hash code by the table size.
        // It only works if the table size is a power of 2.
        // The performance improvement is measurable.
        return indexOrHash & (tableSize - 1);
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
