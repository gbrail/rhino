package org.mozilla.javascript;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class implements a map of property keys to indices. It's used to implement the fast-path
 * mode for a slot map.
 */
public class PropertyMap {
    public static final PropertyMap ROOT = new PropertyMap(null, -1, null);

    private final Object key;
    private final int level;
    private final PropertyMap parent;
    private final ConcurrentHashMap<Object, PropertyMap> children = new ConcurrentHashMap<>();

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
        PropertyMap newMap = children.get(key);
        if (newMap != null) {
            return newMap;
        }
        newMap = new PropertyMap(key, level + 1, this);
        children.put(key, newMap);
        return newMap;
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
        PropertyMap map = this;
        while (map != null) {
            if (Objects.equals(key, map.key)) {
                return map.level;
            }
            map = map.parent;
        }
        return -1;
    }
}
