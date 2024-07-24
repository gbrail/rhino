/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/*
 * This class implements the SlotMap interface using an embedded hash table. This hash table
 * has the minimum overhead needed to get the job done. In particular, it embeds the Slot
 * directly into the hash table rather than creating an intermediate object, which seems
 * to have a measurable performance benefit.
 */

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public class EmbeddedSlotMap implements SlotMap {

    private Slot[] slots;

    private Slot[] orderedSlots;
    private int orderedSize;

    private int count;
    private int pendingDeletes;
    private boolean cleanedNulls;
    private OptionalLong keyHash = OptionalLong.empty();

    // initial slot array size, must be a power of 2
    private static final int INITIAL_SLOT_SIZE = 4;
    // After a bunch of deletes, clean up
    private static final int DELETE_THRESHOLD = 10;
    // Compute hash indices for this number of properties
    public static final int NUM_FAST_PROPERTIES = 16;

    static {
        // A reminder that our hash algorithm doesn't work with large numbers
        if (NUM_FAST_PROPERTIES >= 32) {
            throw new AssertionError("Hash algorithm needs value to be less than 32");
        }
    }

    private final class Iter implements Iterator<Slot> {
        private int pos;

        @Override
        public boolean hasNext() {
            while ((pos < orderedSize) && (orderedSlots[pos] == null)) {
                // Need to skip nulls because of how we do deletes
                pos++;
            }
            return pos < orderedSize;
        }

        @Override
        public Slot next() {
            while ((pos < orderedSize) && (orderedSlots[pos] == null)) {
                // Need to skip nulls because of how we do deletes
                pos++;
            }
            if (pos < orderedSize) {
                return orderedSlots[pos++];
            }
            throw new NoSuchElementException();
        }
    }

    public EmbeddedSlotMap() {}

    public EmbeddedSlotMap(int initialCapacity) {
        // Calculate the smallest power of 2 that leaves more slots than keys
        int minSlots = (initialCapacity * 4) / 3;
        slots = new Slot[getNextPowerOfTwo(minSlots)];
        orderedSlots = new Slot[getNextPowerOfTwo(initialCapacity)];
    }

    @Override
    public int size() {
        return count;
    }

    @Override
    public boolean isEmpty() {
        return count == 0;
    }

    @Override
    public Iterator<Slot> iterator() {
        return new Iter();
    }

    /** Locate the slot with the given name or index. */
    @Override
    public Slot query(Object key, int index) {
        if (slots == null) {
            return null;
        }

        int indexOrHash = (key != null ? key.hashCode() : index);
        int slotIndex = getSlotIndex(slots.length, indexOrHash);
        for (Slot slot = slots[slotIndex]; slot != null; slot = slot.next) {
            if (indexOrHash == slot.indexOrHash && Objects.equals(slot.name, key)) {
                return slot;
            }
        }
        return null;
    }

    @Override
    public Optional<FastQueryResult> queryFastIndex(Object name, int index) {
        Slot slot = query(name, index);
        if (slot != null) {
            if (slot.orderedIndex < NUM_FAST_PROPERTIES) {
                return Optional.of(
                        new FastQueryResult(slot.orderedIndex, getFastDiscriminator(name, index)));
            } else {
                return Optional.of(
                        new FastQueryResult(
                                slot.orderedIndex, getIdentityDiscriminator(name, index)));
            }
        }
        return Optional.empty();
    }

    @Override
    public Slot queryFast(int fastIndex) {
        return orderedSlots[fastIndex];
    }

    private FastTester getFastDiscriminator(Object expectedName, int expectedIndex) {
        // Need to store this so that the discriminator is invalidated
        // when the hash changes.
        long hash = getFastHash();
        return (map, name, index) -> {
            if (hash != map.getFastHash()) {
                return false;
            }
            if (name == null) {
                return expectedIndex == index;
            }
            return Objects.equals(expectedName, name);
        };
    }

    private FastTester getIdentityDiscriminator(Object expectedName, int expectedIndex) {
        return (m, name, index) -> {
            SlotMap map = m;
            if (map instanceof SlotMapContainer) {
                m = ((SlotMapContainer) map).getChild();
            }
            if (name == null) {
                return expectedIndex == index;
            }
            return Objects.equals(expectedName, name) && Objects.equals(this, map);
        };
    }

    private void forceRehash() {
        keyHash = OptionalLong.empty();
    }

    @Override
    public long getFastHash() {
        return keyHash.orElseGet(() -> computeKeyHash());
    }

    private long computeKeyHash() {
        long hash = 0L;
        for (int i = 0; i < orderedSize && i < NUM_FAST_PROPERTIES; i++) {
            Slot slot = orderedSlots[i];
            if (slot != null) {
                // Account for ordering, so that objects with the same keys
                // don't end up with the same hash. Shifting produces enough of
                // a difference -- adding or oring doesn't because arithmetic
                hash += ((long) slot.indexOrHash << (long) i);
            }
        }
        keyHash = OptionalLong.of(hash);
        return hash;
    }

    /**
     * Locate the slot with given name or index, and create a new one if necessary.
     *
     * @param key either a String or a Symbol object that identifies the property
     * @param index index or 0 if slot holds property name.
     */
    @Override
    public Slot modify(Object key, int index, int attributes) {
        final int indexOrHash = (key != null ? key.hashCode() : index);
        Slot slot;

        if (slots != null) {
            final int slotIndex = getSlotIndex(slots.length, indexOrHash);
            for (slot = slots[slotIndex]; slot != null; slot = slot.next) {
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

    private void createNewSlot(Slot newSlot) {
        if (count == 0) {
            // Always throw away old slots if any on empty insert.
            slots = new Slot[INITIAL_SLOT_SIZE];
        }

        // Check if the table is not too full before inserting.
        if (4 * (count + 1) > 3 * slots.length) {
            // table size must be a power of 2 -- always grow by x2!
            Slot[] newSlots = new Slot[slots.length * 2];
            copyTable(slots, newSlots);
            slots = newSlots;
        }

        insertNewSlot(newSlot);

        if (newSlot.orderedIndex < NUM_FAST_PROPERTIES) {
            forceRehash();
        }
    }

    @Override
    public <S extends Slot> S compute(Object key, int index, SlotComputer<S> c) {
        final int indexOrHash = (key != null ? key.hashCode() : index);

        if (slots != null) {
            Slot slot;
            final int slotIndex = getSlotIndex(slots.length, indexOrHash);
            Slot prev = slots[slotIndex];
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
                        slots[slotIndex] = newSlot;
                    } else {
                        prev.next = newSlot;
                    }
                    newSlot.next = slot.next;
                    // Replace slot in ordered list
                    newSlot.orderedIndex = slot.orderedIndex;
                    orderedSlots[newSlot.orderedIndex] = newSlot;
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
        if (slots == null) {
            slots = new Slot[INITIAL_SLOT_SIZE];
        }
        insertNewSlot(newSlot);
    }

    private void insertNewSlot(Slot newSlot) {
        if (orderedSlots == null) {
            orderedSlots = new Slot[INITIAL_SLOT_SIZE];
        } else if (orderedSize >= orderedSlots.length) {
            Slot[] newOrdered = new Slot[orderedSlots.length * 2];
            System.arraycopy(orderedSlots, 0, newOrdered, 0, orderedSlots.length);
            orderedSlots = newOrdered;
        }
        newSlot.orderedIndex = orderedSize;
        orderedSlots[orderedSize++] = newSlot;
        count++;
        addKnownAbsentSlot(slots, newSlot);
    }

    private void removeSlot(Slot slot, Slot prev, int ix, Object key) {
        count--;
        // remove slot from hash table
        if (prev == slot) {
            slots[ix] = slot.next;
        } else {
            prev.next = slot.next;
        }

        // Mark ordered slot null. After a number of deletions we'll clean it up.
        orderedSlots[slot.orderedIndex] = null;
        if (++pendingDeletes > DELETE_THRESHOLD) {
            cleanUpNulls();
            pendingDeletes = 0;
            cleanedNulls = true;
        }

        if (slot.orderedIndex < NUM_FAST_PROPERTIES) {
            forceRehash();
        }
    }

    private static void copyTable(Slot[] oldSlots, Slot[] newSlots) {
        for (Slot slot : oldSlots) {
            while (slot != null) {
                Slot nextSlot = slot.next;
                slot.next = null;
                addKnownAbsentSlot(newSlots, slot);
                slot = nextSlot;
            }
        }
    }

    private void cleanUpNulls() {
        Slot[] newOrderedSlots = new Slot[orderedSlots.length];
        int newPos = 0;
        for (int pos = 0; pos < orderedSize; pos++) {
            Slot slot = orderedSlots[pos];
            if (slot != null) {
                slot.orderedIndex = newPos;
                newOrderedSlots[newPos++] = slot;
            }
        }
        orderedSlots = newOrderedSlots;
        orderedSize = newPos;
        // Every time we change indices we must rehash
        forceRehash();
    }

    /**
     * Add slot with keys that are known to absent from the table. This is an optimization to use
     * when inserting into empty table, after table growth or during deserialization.
     */
    private static void addKnownAbsentSlot(Slot[] addSlots, Slot slot) {
        final int insertPos = getSlotIndex(addSlots.length, slot.indexOrHash);
        Slot old = addSlots[insertPos];
        addSlots[insertPos] = slot;
        slot.next = old;
    }

    private static int getSlotIndex(int tableSize, int indexOrHash) {
        // This is a Java trick to efficiently "mod" the hash code by the table size.
        // It only works if the table size is a power of 2.
        // The performance improvement is measurable.
        return indexOrHash & (tableSize - 1);
    }

    private static int getNextPowerOfTwo(int min) {
        int p = INITIAL_SLOT_SIZE;
        while (p < min) {
            p <<= 1;
        }
        return p;
    }
}
