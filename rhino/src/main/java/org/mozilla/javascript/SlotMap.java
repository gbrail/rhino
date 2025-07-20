/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

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

    @SuppressWarnings("AndroidJdkLibsChecker")
    // https://developer.android.com/reference/java/lang/FunctionalInterface added in API level 24
    @FunctionalInterface
    public interface SlotComputer<S extends Slot> {
        S compute(
                Object key,
                int index,
                Slot existing,
                CompoundOperationMap mutableMap,
                SlotMapOwner owner);
    }

    /** Return the size of the map. */
    int size();

    /** Return whether the map is empty. */
    boolean isEmpty();

    /**
     * Return the Slot that matches EITHER "key" or "index". (It will use "key" if it is not null,
     * and otherwise "index".) If no slot exists, then create a default slot class.
     *
     * @param key The key for the slot, which should be a String or a Symbol.
     * @param index if key is zero, then this will be used as the key instead.
     * @param attributes the attributes to be set on the slot if a new slot is created. Existing
     *     slots will not be modified.
     * @return a Slot, which will be created anew if no such slot exists.
     */
    Slot modify(SlotMapOwner owner, Object key, int index, int attributes);

    /**
     * Retrieve the slot at EITHER key or index, or return null if the slot cannot be found.
     *
     * @param key The key for the slot, which should be a String or a Symbol.
     * @param index if key is zero, then this will be used as the key instead.
     * @return either the Slot that matched the key and index, or null
     */
    Slot query(Object key, int index);

    /**
     * Replace the value of key with the slot computed by the "compute" method. If "compute" throws
     * an exception, make no change. If "compute" returns null, remove the mapping, otherwise,
     * replace any existing mapping with the result of "compute", and create a new mapping if none
     * exists. This is equivalent to the "compute" method on the Map interface, which simplifies
     * code and is more efficient than making multiple calls to this interface. In order to allow
     * use of multiple Slot subclasses, this function is templatized.
     */
    default <S extends Slot> S compute(
            SlotMapOwner owner, Object key, int index, SlotComputer<S> compute) {
        try (var mutableMap = owner.startCompoundOp(true)) {
            return mutableMap.compute(owner, mutableMap, key, index, compute);
        }
    }

    <S extends Slot> S compute(
            SlotMapOwner owner,
            CompoundOperationMap mutableMap,
            Object key,
            int index,
            SlotComputer<S> compute);

    /**
     * Insert a new slot to the map. Both "name" and "indexOrHash" must be populated. Note that
     * ScriptableObject generally adds slots via the "modify" method.
     */
    void add(SlotMapOwner owner, Slot newSlot);

    default int dirtySize() {
        return size();
    }

    default CompoundOperationMap startCompoundOp(SlotMapOwner owner, boolean forWriting) {
        return new CompoundOperationMap(owner);
    }
    
    /**
     * If the slot map supports fast property keys, it should return a Key instance that may be used
     * with "queryFast" to retrieve the slot associated with the specified key.
     */
    default Key getFastQueryKey(Object key) {
        return null;
    }

    /**
     * If the slot map supports fast property keys, it should return a Key instance that may be used
     * with "modifyFast" to modify the slot associated with the specified key.
     */
    default Key getFastModifyKey(Object key, int attributes, boolean isExtensible) {
        return null;
    }

    /**
     * If the slot map supports fast property keys, it should return a Key instance that may be used
     * with "addFast" to add a new slot.
     */
    default Key getFastAddKey(Object key) {
        return null;
    }

    /**
     * If the slot map supports fast property keys, it should return a Key instance that just
     * returns true if the specified map is compatible.
     */
    default Key getFastWildcardKey() {
        return null;
    }

    /**
     * If the slot map supports fast property keys, if a valid key was returned and has the same
     * shape, then we must return the slot associated with the key.
     */
    default Slot queryFast(Key key) {
        throw new UnsupportedOperationException("queryFast");
    }

    /**
     * If the slot map supports fast property keys, if a vz`alid key was returned and has the same
     * shape, then we must return the slot associated with the key.
     */
    default Slot modifyFast(Key key) {
        throw new UnsupportedOperationException("modifyFast");
    }

    /** If the slot map supports fast property keys, this method adds a new slot. */
    default void addFast(Key key, Slot newSlot) {
        throw new UnsupportedOperationException("addFast");
    }

    /**
     * The Key interface is used when doing fast-path operations. The "isCompatible" method
     * <em>must</em> be called on every operation, and must return "true" before any other fast path
     * operation can be made on that particular key. Furthermore, keys may not be mixed -- a key
     * from "queryFast" <em>must</em> not be used with "modifyFast" and vice versa.
     */
    interface Key {
        /**
         * Users of the "queryFast" and "modifyFast" methods <em>must</em> call this method and
         * ensure that it returns true before <em>each</em> operation.
         */
        boolean isCompatible(SlotMap otherMap);

        /**
         * When used with "modifyFast", callers <em>must</em> call this method to determine if the
         * saved operation extends the properties on the object before using it.
         */
        default boolean isExtending() {
            return false;
        }
        ;
    }
}
