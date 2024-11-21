package org.mozilla.javascript;

import java.util.Objects;

public class PropertyMap {
    public static PropertyMap EMPTY = new PropertyMap();

    // Hard coded for now
    private static final int NUM_SLOTS = 32;

    private final int position;
    private final PropSlot[] slots = new PropSlot[NUM_SLOTS];
    private ChildSlot[] children = new ChildSlot[NUM_SLOTS];

    /** Create an empty map -- should only be used for testing. */
    public static PropertyMap emptyMap() {
        return new PropertyMap();
    }

    /** Make an empty property map */
    private PropertyMap() {
        position = -1;
    }

    /** Make a property map that is a child of the parent, which means copying its keys. */
    private PropertyMap(PropertyMap parent, Object key) {
        position = parent.position + 1;
        // Copy the key hash table
        for (int i = 0; i < NUM_SLOTS; i++) {
            PropSlot s = parent.slots[i];
            while (s != null) {
                PropSlot ns = new PropSlot(s.key, s.position);
                ns.next = slots[i];
                slots[i] = ns;
                s = s.next;
            }
        }
        // Insert the new key
        PropSlot ns = new PropSlot(key, position);
        int bucket = getSlotIndex(key);
        ns.next = slots[bucket];
        slots[bucket] = ns;
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
    public PropertyMap add(Object key) {
        int bucket = getSlotIndex(key);
        ChildSlot s = children[bucket];
        while (s != null) {
            if (Objects.equals(key, s.key)) {
                return s.map;
            }
            s = s.next;
        }

        PropertyMap newMap = new PropertyMap(this, key);
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
        PropertyMap map;
        ChildSlot next;

        ChildSlot(Object key, PropertyMap map) {
            this.key = key;
            this.map = map;
        }
    }
}
