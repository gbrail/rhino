/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This class implements a map of property keys to indices. It's used to implement the fast-path
 * mode for a slot map.
 */
public class PropertyMap {
    private final Slot.Key key;
    private final int level;
    private final Map<Slot.Key, PropertyMap> children = new HashMap<>();
    private final Map<Slot.Key, Integer> keys;

    public static final class AddResult {
        private final int index;
        private final PropertyMap map;

        AddResult(int index) {
            this.index = index;
            this.map = null;
        }

        AddResult(int index, PropertyMap map) {
            this.index = index;
            this.map = map;
        }

        public boolean hasMap() {
            return map != null;
        }

        public int getIndex() {
            return index;
        }

        public PropertyMap getMap() {
            return map;
        }
    }

    /** Construct a root map, which has no entries. */
    public PropertyMap() {
        this.key = null;
        this.level = -1;
        keys = Collections.emptyMap();
    }

    /**
     * Construct a map from the parent map that adds the new key. It contains indices to all keys in
     * the parent map.
     */
    private PropertyMap(Slot.Key key, int level, PropertyMap parent) {
        this.key = key;
        this.level = level;
        keys = new HashMap<>(parent.keys);
        keys.put(key, level);
    }

    public int getLevel() {
        return level;
    }

    /** Given a key, return the index that it's at in this property map, or -1 if not found. */
    public int get(Slot.Key key) {
        Integer ix = keys.get(key);
        return ix == null ? -1 : ix;
    }

    /**
     * Given a key, if the key is already in this property map chain, then return the index where it
     * may be found (in other words, the same as get). Otherwise, if the map is an existing child of
     * this one, then return the map and its index. Finally, if the map is a new child (we have
     * never seen a property with this name in this order), then create a new child and return it
     * and its index.
     */
    public AddResult add(Slot.Key key) {
        Integer found = keys.get(key);
        if (found != null) {
            return new AddResult(found);
        }
        PropertyMap child = children.computeIfAbsent(key, k -> new PropertyMap(k, level + 1, this));
        assert (child.level == level + 1);
        return new AddResult(child.level, child);
    }

    /** Dump the map for debugging. */
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

    /** Property maps are compared using object identity. */
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }
}
