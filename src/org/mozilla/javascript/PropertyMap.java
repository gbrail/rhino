/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.Objects;
import java.util.WeakHashMap;

/**
 * This class implements a map of property keys to indices. It's used to implement the fast-path
 * mode for a slot map.
 */
public class PropertyMap {
    // A single, thread-safe, root property map that everyone
    // inherits from.
    public static final PropertyMap ROOT = new PropertyMap(null, -1, null);

    // Hash table is sized for about 30 entries
    private static final int HASH_SIZE = 64;

    private final Object key;
    private final int level;
    private final PropertyMap parent;
    private final WeakHashMap<Object, PropertyMap> children = new WeakHashMap<>();
    private final Entry[] entries = new Entry[HASH_SIZE];

    private PropertyMap(Object key, int level, PropertyMap parent) {
        this.key = key;
        this.level = level;
        this.parent = parent;
        if (parent != null) {
            // This shallow copy works because we never modify existing entries.
            // Or do we? TODO we need to figure this out!
            assert (entries.length == parent.entries.length);
            System.arraycopy(parent.entries, 0, this.entries, 0, HASH_SIZE);
            addEntry(key, level);
        }
    }

    public int getLevel() {
        return level;
    }

    /**
     * Return a new PropertyMap that extends the current map with the new key. This may be a new or
     * an existing property map -- that's the nature of property maps.
     */
    public PropertyMap add(Object key) {
        synchronized (children) {
            PropertyMap newMap = children.get(key);
            if (newMap != null) {
                return newMap;
            }
            newMap = new PropertyMap(key, level + 1, this);
            children.put(key, newMap);
            return newMap;
        }
    }

    /**
     * Attempt to remove the key from the property map. If the key is the last entry in the map,
     * then return the previous map. Otherwise, return null, which indicates that the property map
     * constraints no longer apply and can't be used with this object.
     */
    public PropertyMap remove(Object key) {
        if (Objects.equals(key, this.key)) {
            return parent;
        }
        return null;
    }

    /** Find the index of the map entry with the specified key, or null if the key is not found. */
    public int find(Object key) {
        int hashCode = key.hashCode();
        Entry bucket = entries[hash(hashCode)];
        while (bucket != null) {
            if (Objects.equals(key, bucket.key)) {
                return bucket.index;
            }
            bucket = bucket.next;
        }
        return -1;
    }

    private static int hash(int hashCode) {
        // This is a Java trick to efficiently "mod" the hash code by the table size.
        // It only works if the table size is a power of 2.
        // The performance improvement is measurable.
        return hashCode & (HASH_SIZE - 1);
    }

    /** Add a key to the table. */
    private void addEntry(Object key, int index) {
        Entry newEntry = new Entry(key, index);
        int ix = hash(key.hashCode());
        if (entries[ix] != null) {
            newEntry.next = entries[ix];
        }
        entries[ix] = newEntry;
    }

    private static final class Entry {
        final Object key;
        final int index;
        Entry next;

        Entry(Object key, int index) {
            this.key = key;
            this.index = index;
            next = null;
        }
    }
}
