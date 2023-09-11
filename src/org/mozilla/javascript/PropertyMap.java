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

    private final Object key;
    private final int level;
    private final PropertyMap parent;
    private final WeakHashMap<Object, PropertyMap> children = new WeakHashMap<>();

    private PropertyMap(Object key, int level, PropertyMap parent) {
        this.key = key;
        this.level = level;
        this.parent = parent;
    }

    public int getLevel() {
        return level;
    }

    /**
     * Return a new PropertyMap that extends the current map with the new key. This may be a new or
     * an existing property map -- that's the nature of property maps.
     */
    public PropertyMap add(Object key) {
        if (Objects.equals(key, this.key)) {
            return this;
        }
        synchronized (children) {
            return children.computeIfAbsent(key, k -> new PropertyMap(k, level + 1, this));
        }
    }

    /**
     * Attempt to remove the key from the property map. If the key is the last entry in the map,
     * then return the previous map. Otherwise, return null, which indicates that the property map
     * constraints no longer apply and can't be used with this object.
     */
    public PropertyMap remove(Object key) {
        /* TODO not working for now
        if (Objects.equals(key, this.key)) {
            return parent;
        }
        */
        return null;
    }

    public void print() {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < level; i++) {
            indent.append(' ');
        }
        System.out.println(indent.toString() + key + ": " + level);
        for (PropertyMap child : children.values()) {
            child.print();
        }
    }

    public static void printTree() {
        ROOT.print();
    }
}
