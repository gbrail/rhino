/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.Iterator;

/**
 * This class holds the various SlotMaps of various types, and knows how to atomically switch
 * between them when we need to so that we use the right data structure at the right time.
 */
class SlotMapContainer implements SlotMap {
    private static final int DEFAULT_SIZE = 10;

    protected SlotMap map;

    SlotMapContainer() {
        this(DEFAULT_SIZE);
    }

    SlotMapContainer(int initialSize) {
        if (initialSize > ArraySlotMap.SIZE_LIMIT) {
            map = new HashSlotMap();
        } else {
            map = new ArraySlotMap();
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
        checkLimits();
        return map.modify(key, index, attributes);
    }

    @Override
    public <S extends Slot> S compute(Object key, int index, SlotComputer<S> c) {
        checkLimits();
        return map.compute(key, index, c);
    }

    @Override
    public Slot query(Object key, int index) {
        return map.query(key, index);
    }

    @Override
    public void add(Slot newSlot) {
        checkLimits();
        map.add(newSlot);
    }

    @Override
    public int getFastQueryIndex(Object key, int index) {
        return map.getFastQueryIndex(key, index);
    }

    @Override
    public boolean testFastQuery(SlotMap m, int index) {
        SlotMap realMap = m;
        if (m instanceof SlotMapContainer) {
            realMap = ((SlotMapContainer) m).map;
        }
        return map.testFastQuery(realMap, index);
    }

    @Override
    public Slot queryFast(int index) {
        return map.queryFast(index);
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

    /** Give subclasses the opportunity to ask to be replaced with a more generic HashSlotMap. */
    protected void checkLimits() {
        if (map.isLimitReached()) {
            SlotMap newMap = new HashSlotMap();
            for (Slot s : map) {
                newMap.add(s);
            }
            map = newMap;
        }
    }
}
