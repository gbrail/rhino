/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.Iterator;
import java.util.OptionalInt;

/**
 * This class holds the various SlotMaps of various types, and knows how to atomically switch
 * between them when we need to so that we use the right data structure at the right time.
 */
class SlotMapContainer implements SlotMap {

    /**
     * Once the object has this many properties in it, we will replace the EmbeddedSlotMap with
     * HashSlotMap. We can adjust this parameter to balance performance for typical objects versus
     * performance for huge objects with many collisions.
     */
    private static final int LARGE_HASH_SIZE = 2000;
    /**
     * A slot map hash table, once it reaches this size, will be promoted to a more efficient
     * representation.
     */
    private static final int SMALL_HASH_SIZE = 32;

    private static final int DEFAULT_SIZE = 10;

    protected SlotMap map;

    SlotMapContainer() {
        this(DEFAULT_SIZE);
    }

    SlotMapContainer(int initialSize) {
        if (initialSize > LARGE_HASH_SIZE) {
            map = new HashSlotMap();
        } else { // if (initialSize > SMALL_HASH_SIZE) {
            map = new EmbeddedSlotMap();
            // } else {
            //    map = new ShapedSlotMap();
        }
    }

    @Override
    public int size() {
        return map.size();
    }

    public int dirtySize() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public Slot modify(Object key, int index, int attributes) {
        checkMapSize();
        return map.modify(key, index, attributes);
    }

    @Override
    public <S extends Slot> S compute(Object key, int index, SlotComputer<S> c) {
        return map.compute(key, index, c);
    }

    @Override
    public Slot query(Object key, int index) {
        return map.query(key, index);
    }

    @Override
    public void add(Slot newSlot) {
        checkMapSize();
        map.add(newSlot);
    }

    @Override
    public Iterator<Slot> iterator() {
        return map.iterator();
    }

    public long readLock() {
        // No locking in the default implementation
        return 0L;
    }

    public void unlockRead(long stamp) {
        // No locking in the default implementation
    }

    /**
     * Before inserting a new item in the map, check and see if we need to expand from the embedded
     * map to a HashMap that is more robust against large numbers of hash collisions.
     */
    protected void checkMapSize() {
        if (!(map instanceof HashSlotMap) && map.size() >= LARGE_HASH_SIZE) {
            replaceMap(new HashSlotMap(map.size() + 1));
        } else if (map instanceof ShapedSlotMap && map.size() >= SMALL_HASH_SIZE) {
            replaceMap(new EmbeddedSlotMap(map.size() + 1));
        }
    }

    private void replaceMap(SlotMap newMap) {
        for (Slot s : map) {
            newMap.add(s);
        }
        map = newMap;
    }

    @Override
    public ObjectShape getShape() {
        return map.getShape();
    }

    @Override
    public OptionalInt queryFastIndex(Object name, int index) {
        return map.queryFastIndex(name, index);
    }

    @Override
    public Slot queryFast(int fastIndex) {
        return map.queryFast(fastIndex);
    }

    @Override
    public Slot addFast(Object name, int index, ObjectShape newShape) {
        return map.addFast(name, index, newShape);
    }
}
