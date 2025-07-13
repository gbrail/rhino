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
    @FunctionalInterface
    public interface SlotComputer<S extends Slot> {
        S compute(Object key, int index, Slot existing);
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
    <S extends Slot> S compute(SlotMapOwner owner, Object key, int index, SlotComputer<S> compute);

    /**
     * Insert a new slot to the map. Both "name" and "indexOrHash" must be populated. Note that
     * ScriptableObject generally adds slots via the "modify" method.
     */
    void add(SlotMapOwner owner, Slot newSlot);

    default long readLock() {
        // No locking in the default implementation
        return 0L;
    }

    default void unlockRead(long stamp) {
        // No locking in the default implementation
    }

    default int dirtySize() {
        return size();
    }

    /**
     * If the slot map supports fast property keys, it should return a FastKey instance that may be
     * used to fetch the specified property.
     */
    default ScriptableObject.FastKey getFastQueryKey(Object key) {
        return DEFAULT_KEY;
    }

    /**
     * If the slot map supports fast property keys, it should return a FastKey instance that may be
     * used to modify the specified property.
     */
    default ScriptableObject.FastWriteKey getFastModifyKey(
            Object key, int attributes, boolean isExtensible) {
        return DEFAULT_WRITE_KEY;
    }

    /**
     * If the slot map supports fast property keys, if a valid key was returned and has the same
     * shape, then we must return the slot associated with the key.
     */
    default Slot queryFast(ScriptableObject.FastKey key) {
        return null;
    }

    /**
     * If the slot map supports fast property keys, if a valid key was returned and has the same
     * shape, then we must return the slot associated with the key.
     */
    default Slot modifyFast(ScriptableObject.FastWriteKey key) {
        return null;
    }

    ScriptableObject.FastKey DEFAULT_KEY =
            new ScriptableObject.FastKey() {
                @Override
                public boolean isPresent() {
                    return false;
                }

                @Override
                public boolean isSameShape(ScriptableObject so) {
                    return false;
                }
            };
    ScriptableObject.FastWriteKey DEFAULT_WRITE_KEY =
            new ScriptableObject.FastWriteKey() {
                @Override
                public boolean isPresent() {
                    return false;
                }

                @Override
                public boolean isSameShape(ScriptableObject so) {
                    return false;
                }
            };
}
