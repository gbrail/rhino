package org.mozilla.javascript;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class PropertyMap {
    public static PropertyMap EMPTY = new PropertyMap();

    private final int position;
    private final Map<Object, Integer> keys;
    private final WeakHashMap<Object, PropertyMap> children;

    /**
     * Create an empty map -- should only be used for testing.
     */
    public static PropertyMap emptyMap() {
        return new PropertyMap();
    }

    /** Make an empty property map */
    private PropertyMap() {
        position = -1;
        keys = Collections.emptyMap();
        children = new WeakHashMap<>();
    }

    /** Make a property map that is a child of the parent, which means copying its keys. */
    private PropertyMap(PropertyMap parent, Object key) {
        position = parent.position + 1;
        children = new WeakHashMap<>();
        keys = new HashMap<>(parent.keys);
        keys.put(key, position);
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
        return children.computeIfAbsent(key, (k) -> new PropertyMap(this, k));
    }

    /**
     * If "key" is in this property map, return its relative position in the map, and otherwise
     * return -1.
     */
    public int find(Object key) {
        Integer pos = keys.get(key);
        return pos == null ? -1 : pos;
    }
}
