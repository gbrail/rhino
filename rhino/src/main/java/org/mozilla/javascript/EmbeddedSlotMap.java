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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Predicate;

public class EmbeddedSlotMap implements SlotMap {

    private Slot[] slots;

    private ArrayList<Slot> orderedSlots = new ArrayList<>();

    private int count;
    private int pendingDeletes;
    private boolean manyDeletes;

    // initial slot array size, must be a power of 2
    private static final int INITIAL_SLOT_SIZE = 4;
    // After a bunch of deletes, clean up
    private static final int DELETE_THRESHOLD = 10;

    private final class Iter implements Iterator<Slot> {
        private int pos;

        @Override
        public boolean hasNext() {
            while ((pos < orderedSlots.size()) && (orderedSlots.get(pos) == null)) {
                // Need to skip nulls because of how we do deletes
                pos++;
            }
            return pos < orderedSlots.size();
        }

        @Override
        public Slot next() {
            while ((pos < orderedSlots.size()) && (orderedSlots.get(pos) == null)) {
                pos++;
            }
            if (pos < orderedSlots.size()) {
                return orderedSlots.get(pos++);
            }
            throw new NoSuchElementException();
        }
    }

    public EmbeddedSlotMap() {}

    public EmbeddedSlotMap(int initialCapacity) {
        // Calculate the smallest power of 2 that leaves more slots than keys
        int minSlots = (initialCapacity * 4) / 3;
        int numSlots = INITIAL_SLOT_SIZE;
        while (numSlots < minSlots) {
            numSlots <<= 1;
        }
        slots = new Slot[numSlots];
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
    public OptionalInt queryFastIndex(Object name, int index) {
        Slot slot = query(name, index);
        return slot == null ? OptionalInt.empty() : OptionalInt.of(slot.orderedIndex);
    }

    @Override
    public Slot queryFast(int fastIndex) {
        return orderedSlots.get(fastIndex);
    }

    @Override
    public Predicate<SlotMap> getDiscriminator() {
        return (m) -> !manyDeletes && Objects.equals(m, this);
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
                    orderedSlots.set(newSlot.orderedIndex, newSlot);
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
        ++count;
        newSlot.orderedIndex = orderedSlots.size();
        orderedSlots.add(newSlot);
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
        orderedSlots.set(slot.orderedIndex, null);
        if (++pendingDeletes > DELETE_THRESHOLD) {
            manyDeletes = true;
            cleanUpNulls();
            pendingDeletes = 0;
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
        ArrayList<Slot> newSlots = new ArrayList<>(count);
        for (Slot slot : orderedSlots) {
            if (slot != null) {
                slot.orderedIndex = newSlots.size();
                newSlots.add(slot);
            }
        }
        orderedSlots = newSlots;
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
}
