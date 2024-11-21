package org.mozilla.javascript;

import java.util.Objects;

public class ObjectShape {
    public static ObjectShape EMPTY = new ObjectShape();

    // Hard coded for now
    private static final int NUM_SLOTS = 32;

    private final int position;
    private final PropSlot[] slots = new PropSlot[NUM_SLOTS];
    private ChildSlot[] children = new ChildSlot[NUM_SLOTS];

    /** Create an empty map -- should only be used for testing. */
    public static ObjectShape emptyMap() {
        return new ObjectShape();
    }

    /** Make an empty property map */
    private ObjectShape() {
        position = -1;
    }

    /** Make a property map that is a child of the parent, which means copying its keys. */
    private ObjectShape(ObjectShape parent, Object key) {
        position = parent.position + 1;
        // We can actually copy the old slots because we'll insert
        // any new keys at the start of one of the buckets.
        System.arraycopy(parent.slots, 0, slots, 0, NUM_SLOTS);
        // Insert the new key
        PropSlot ns = new PropSlot(key, position);
        int newBucket = getSlotIndex(key);
        ns.next = slots[newBucket];
        slots[newBucket] = ns;
    }

    /** Return the relative order of the final property in this map. */
    public int getPosition() {
        return position;
    }

    /**
     * Return the property map that would exist if "key" was the next entry in the ordered list of
     * properties. This may return an existing map or create a new one. "getPosition" may be used to
     * understand at what level we are in the new map.
     */
    public synchronized ObjectShape add(Object key) {
        int bucket = getSlotIndex(key);
        ChildSlot s = children[bucket];
        while (s != null) {
            if (Objects.equals(key, s.key)) {
                return s.map;
            }
            s = s.next;
        }

        ObjectShape newMap = new ObjectShape(this, key);
        ChildSlot ns = new ChildSlot(key, newMap);
        ns.next = children[bucket];
        children[bucket] = ns;
        return newMap;
    }

    /**
     * If "key" is in this property map, return its relative position in the map, and otherwise
     * return -1.
     */
    public int find(Object key) {
        PropSlot s = slots[getSlotIndex(key)];
        while (s != null) {
            if (Objects.equals(key, s.key)) {
                return s.position;
            }
            s = s.next;
        }
        return -1;
    }

    private static int getSlotIndex(Object key) {
        return key.hashCode() & (NUM_SLOTS - 1);
    }

    private static final class PropSlot {
        Object key;
        int position;
        PropSlot next;

        PropSlot(Object key, int position) {
            this.key = key;
            this.position = position;
        }
    }

    private static final class ChildSlot {
        Object key;
        ObjectShape map;
        ChildSlot next;

        ChildSlot(Object key, ObjectShape map) {
            this.key = key;
            this.map = map;
        }
    }
}
