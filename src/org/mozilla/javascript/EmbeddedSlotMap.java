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

public class EmbeddedSlotMap implements SlotMap {
    /** The actual hash buckets */
    private Bucket[] buckets;
    /** The list of slots, in insertion order. */
    private Slot[] orderedSlots;
    /** The number of slots in the map */
    private int length;
    /** The insertion position in the ordered map */
    private int orderedSlotLength;
    /** A map for fast-indexed properties */
    private PropertyMap propertyMap = PropertyMap.ROOT;

    /** Initial number of buckets: must be a power of 2 */
    private static final int INITIAL_BUCKET_COUNT = 4;
    /** Initial number of ordered slots */
    private static final int INITIAL_SLOT_COUNT = 16;
    /** Number of fast properties to support using the property map */
    private static final int NUM_FAST_PROPERTIES = 10;
    /**
     * When we've had this many slots, we're too big and need a HashMap, or perhaps there have been
     * too many deletions.
     */
    private static final int MAX_EFFICIENT_SLOTS = 1000;

    public static final class FastKeyImpl implements FastKey {
        PropertyMap map;
        int index;

        FastKeyImpl(PropertyMap map, int index) {
            this.map = map;
            this.index = index;
        }

        @Override
        public String toString() {
            return "{" + index + "}";
        }
    }

    private final class Iter implements Iterator<Slot> {
        private int index;

        Iter() {
            index = -1;
            moveToNext();
        }

        @Override
        public boolean hasNext() {
            return index < orderedSlotLength;
        }

        @Override
        public Slot next() {
            if (index >= orderedSlotLength) {
                throw new NoSuchElementException();
            }
            Slot ret = orderedSlots[index];
            moveToNext();
            return ret;
        }

        private void moveToNext() {
            ++index;
            // Skip over nulled-out slots -- they represent removals
            while (index < orderedSlotLength && orderedSlots[index] == null) {
                ++index;
            }
        }
    }

    private static final class Bucket {
        Object key;
        int indexOrHash;
        int index;
        Bucket next;

        Bucket(Slot s, int index) {
            this.key = s.name;
            this.indexOrHash = s.indexOrHash;
            this.index = index;
            this.next = null;
        }
    }

    public EmbeddedSlotMap() {}

    @Override
    public int size() {
        return length;
    }

    @Override
    public boolean isEmpty() {
        return length == 0;
    }

    @Override
    public boolean isTooBig() {
        return orderedSlotLength > MAX_EFFICIENT_SLOTS;
    }

    @Override
    public Iterator<Slot> iterator() {
        return new Iter();
    }

    /** Locate the slot with the given name or index. */
    @Override
    public Slot query(Object key, int index) {
        int indexOrHash = computeHash(key, index);
        Bucket b = findBucket(key, indexOrHash);
        if (b != null) {
            return orderedSlots[b.index];
        }
        return null;
    }

    @Override
    public FastKey getFastKey(Object key, int index) {
        int indexOrHash = computeHash(key, index);
        Bucket b = findBucket(key, indexOrHash);
        if (b != null && b.index < NUM_FAST_PROPERTIES && propertyMap != null) {
            return new FastKeyImpl(propertyMap, b.index);
        }
        return null;
    }

    @Override
    public boolean isFastKeyValid(FastKey k) {
        if (k instanceof FastKeyImpl) {
            FastKeyImpl key = (FastKeyImpl) k;
            return (key.map == propertyMap
                    && key.index < orderedSlotLength
                    && orderedSlots[key.index] != null);
        }
        return false;
    }

    @Override
    public Slot queryFastNoCheck(FastKey k) {
        FastKeyImpl key = (FastKeyImpl) k;
        assert key.map == propertyMap;
        assert key.index < orderedSlotLength;
        return orderedSlots[key.index];
    }

    /**
     * Locate the slot with given name or index, and create a new one if necessary.
     *
     * @param key either a String or a Symbol object that identifies the property
     * @param index index or 0 if slot holds property name.
     */
    @Override
    public Slot modify(Object key, int index, int attributes) {
        int indexOrHash = computeHash(key, index);
        Bucket b = findBucket(key, indexOrHash);
        if (b != null) {
            return orderedSlots[b.index];
        }
        // A new slot has to be inserted.
        return createSlot(key, indexOrHash, attributes);
    }

    private Slot createSlot(Object key, int indexOrHash, int attributes) {
        if (length == 0) {
            // Reinitialize if the object has been emptied and re-filled
            buckets = new Bucket[INITIAL_BUCKET_COUNT];
            orderedSlots = new Slot[INITIAL_SLOT_COUNT];
            orderedSlotLength = 0;
            propertyMap = PropertyMap.ROOT;
        } else if (4 * (length + 1) > 3 * buckets.length) {
            // table size must be a power of 2 -- always grow by 2!
            Bucket[] newBuckets = new Bucket[buckets.length * 2];
            copyTable(buckets, newBuckets);
            buckets = newBuckets;
        }

        Slot newSlot = new Slot(key, indexOrHash, attributes);
        insertNewSlot(newSlot);
        return newSlot;
    }

    @Override
    public void replace(Slot oldSlot, Slot newSlot) {
        Bucket b = findBucket(oldSlot.name, oldSlot.indexOrHash);
        if (b != null) {
            orderedSlots[b.index] = newSlot;
        }
    }

    @Override
    public void add(Slot newSlot) {
        if (buckets == null) {
            buckets = new Bucket[INITIAL_BUCKET_COUNT];
        }
        insertNewSlot(newSlot);
    }

    private void insertNewSlot(Slot newSlot) {
        if (orderedSlots == null) {
            orderedSlots = new Slot[INITIAL_SLOT_COUNT];
        }
        if (orderedSlotLength == orderedSlots.length) {
            Slot[] newSlots = new Slot[orderedSlots.length * 2];
            System.arraycopy(orderedSlots, 0, newSlots, 0, orderedSlotLength);
            orderedSlots = newSlots;
        }
        orderedSlots[orderedSlotLength] = newSlot;
        Bucket newBucket = new Bucket(newSlot, orderedSlotLength);
        addKnownAbsentSlot(buckets, newBucket);
        if (orderedSlotLength < NUM_FAST_PROPERTIES) {
            // Record the first few properties in a shared property map
            propertyMap = propertyMap.add(newSlot.name);
        }
        orderedSlotLength++;
        length++;
    }

    @Override
    public void remove(Object key, int index) {
        int indexOrHash = computeHash(key, index);
        if (length != 0) {
            final int slotIndex = getSlotIndex(buckets.length, indexOrHash);
            Bucket prev = buckets[slotIndex];
            Bucket b = prev;
            while (b != null) {
                if (b.indexOrHash == indexOrHash && Objects.equals(b.key, key)) {
                    break;
                }
                prev = b;
                b = b.next;
            }
            if (b != null) {
                // non-configurable
                if ((orderedSlots[b.index].getAttributes() & ScriptableObject.PERMANENT) != 0) {
                    Context cx = Context.getContext();
                    if (cx.isStrictMode()) {
                        throw ScriptRuntime.typeErrorById(
                                "msg.delete.property.with.configurable.false", key);
                    }
                    return;
                }
                --length;
                // remove bucket from hash table
                if (prev == b) {
                    buckets[slotIndex] = b.next;
                } else {
                    prev.next = b.next;
                }

                // Null out ordered list. Since removes are infrequent this is OK.
                orderedSlots[b.index] = null;
            }
        }
    }

    private Bucket findBucket(Object key, int indexOrHash) {
        if (buckets != null) {
            final int slotIndex = getSlotIndex(buckets.length, indexOrHash);
            Bucket b;
            for (b = buckets[slotIndex]; b != null; b = b.next) {
                if (indexOrHash == b.indexOrHash && Objects.equals(b.key, key)) {
                    break;
                }
            }
            if (b != null) {
                return b;
            }
        }
        return null;
    }

    private static void copyTable(Bucket[] oldBuckets, Bucket[] newBuckets) {
        for (Bucket b : oldBuckets) {
            while (b != null) {
                Bucket next = b.next;
                b.next = null;
                addKnownAbsentSlot(newBuckets, b);
                b = next;
            }
        }
    }

    /**
     * Add slot with keys that are known to absent from the table. This is an optimization to use
     * when inserting into empty table, after table growth or during deserialization.
     */
    private static void addKnownAbsentSlot(Bucket[] addBuckets, Bucket b) {
        final int insertPos = getSlotIndex(addBuckets.length, b.indexOrHash);
        Bucket old = addBuckets[insertPos];
        addBuckets[insertPos] = b;
        b.next = old;
    }

    private static int getSlotIndex(int tableSize, int indexOrHash) {
        // This is a Java trick to efficiently "mod" the hash code by the table size.
        // It only works if the table size is a power of 2.
        // The performance improvement is measurable.
        return indexOrHash & (tableSize - 1);
    }

    private static int computeHash(Object key, int index) {
        return key == null ? index : key.hashCode();
    }
}
