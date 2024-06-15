/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.function.BiFunction;

/**
 * This class implements the SlotMap interface using a java.util.HashMap. This class has more
 * overhead than EmbeddedSlotMap, especially because it puts each "Slot" inside an intermediate
 * object. However it is much more resistant to large number of hash collisions than EmbeddedSlotMap
 * and therefore we use this implementation when an object gains a large number of properties.
 */
public class HashSlotMap implements SlotMap {

    private final LinkedHashMap<Slot.Key, Slot> map = new LinkedHashMap<>();

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean has(Slot.Key key) {
        return map.containsKey(key);
    }

    @Override
    public Slot query(Slot.Key key) {
        return map.get(key);
    }

    @Override
    public Slot modify(Slot.Key key, int attributes) {
        return map.computeIfAbsent(key, k -> new Slot(k, attributes));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends Slot> S compute(Slot.Key key, BiFunction<Slot.Key, Slot, S> c) {
        Slot slot = map.compute(key, (k, s) -> c.apply(k, s));
        // We can cast here because the function passsed us already
        // is reqiured to return the right type.
        return (S) slot;
    }

    @Override
    public void add(Slot newSlot) {
        map.put(newSlot.key, newSlot);
    }

    @Override
    public void remove(Slot.Key key) {
        map.computeIfPresent(
                key,
                (k, slot) -> {
                    if (slot.checkIsPermanent()) {
                        // If we return the slot it will remain
                        return slot;
                    }
                    // If we return null the object will be deleted
                    return null;
                });
    }

    @Override
    public Iterator<Slot> iterator() {
        return map.values().iterator();
    }

    @Override
    public long readLock() {
        return 0;
    }

    @Override
    public void unlockRead(long stamp) {}
}
