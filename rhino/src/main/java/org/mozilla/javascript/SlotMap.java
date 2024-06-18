/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.function.BiFunction;

/**
 * A SlotMap is an interface to the main data structure that contains all the "Slots" that back a
 * ScriptableObject. It is the primary property map in Rhino. It is Iterable but does not implement
 * java.util.Map because that comes with a bunch of overhead that we do not need.
 *
 * <p>This class generally has a bit of a strange interface, and its interactions with
 * ScriptableObject are complex. Many attempts to make this interface more elegant have resulted in
 * substantial performance regressions so we are doing the best that we can.
 */
public interface SlotMap extends Iterable<Slot> {
    /** Return the size of the map. */
    int size();

    /** Return whether the map is empty. */
    boolean isEmpty();

    /** Return whether the slot map contains the specified key. */
    boolean has(Slot.Key key);

    /**
     * Return the Slot that matches EITHER "key" or "index". (It will use "key" if it is not null,
     * and otherwise "index".) If no slot exists, then create a default slot class.
     *
     * @param key The key for the slot, which must be a String or a Symbol.
     * @param attributes the attributes to be set on the slot if a new slot is created. Existing
     *     slots will not be modified.
     * @return a Slot, which will be created anew if no such slot exists.
     */
    Slot modify(Slot.Key key, int attributes);

    /**
     * Retrieve the slot at EITHER key or index, or return null if the slot cannot be found.
     *
     * @param key The key for the slot, which must be a String or a Symbol.
     * @return either the Slot that matched the key and index, or null
     */
    Slot query(Slot.Key key);

    /**
     * Replace the value of key with the slot computed by the "compute" method, and set attributes
     * if requested. If "compute" throws an exception, make no change. If "compute" returns null,
     * remove the mapping.
     */
    <S extends Slot> S compute(Slot.Key key, BiFunction<Slot.Key, Slot, S> c);

    /**
     * Insert a new slot to the map. Both "name" and "indexOrHash" must be populated. Note that
     * ScriptableObject generally adds slots via the "modify" method.
     */
    void add(Slot newSlot);

    /**
     * Remove the slot at either "key" or "index".
     *
     * @param key The key for the slot, which should be a String or a Symbol.
     * @param index if key is zero, then this will be used as the key instead.
     */
    void remove(Slot.Key key);

    /**
     * For a thread-safe property map, lock the map for reads in the style of StampedLock.
     */
    long readLock();

    /**
     * Unlock a call to readLock().
     */
    void unlockRead(long stamp);

    /**
     * Return the PropertyMap that backs this SlotMap, if any. This is used for
     * handling inline caching.
     */
    PropertyMap getPropertyMap();

    /**
     * Retrieve the key in the slot referenced by the PropertyMap that matches
     * this object. This will fail with undefined behavior unless the PropertyMap
     * for this object matches the one used to look up the index.
     */
    Slot queryFromCache(int index);

    /**
     * Perform a query and return the property map and index that was used.
     */
    CacheableResult<Slot> queryAndGetCacheInfo(Slot.Key key);

    public static final class CacheableResult<T> {
        private final PropertyMap propertyMap;
        private final int index;
        private final T val;

        CacheableResult(T val) {
            this.val = val;
            this.index = -1;
            this.propertyMap = null;
        }

        CacheableResult(T val, int index, PropertyMap map) {
            this.val = val;
            this.index = index;
            this.propertyMap = map;
        }

        CacheableResult(CacheableResult<?> result, T newValue) {
            this.val = newValue;
            this.index = result.index;
            this.propertyMap = result.propertyMap;
        }

        public PropertyMap getPropertyMap() {
            return propertyMap;
        }

        public int getIndex() {
            return index;
        }

        public T getValue() {
            return val;
        }
    }
}
