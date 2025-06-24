package org.mozilla.javascript;

import java.util.Iterator;

public class ShapedSlotMap implements SlotMap {
    private static final int INITIAL_LENGTH = 8;
    // The value of this type of slot map tapers off with larger objects
    public static final int MAXIMUM_SIZE = 32;

    private Slot[] slots;
    private int length;
    private Shape shape;

    public ShapedSlotMap() {
        slots = new Slot[INITIAL_LENGTH];
        shape = Shape.EMPTY;
    }

    @Override
    public int size() {
        return length;
    }

    @Override
    public boolean isEmpty() {
        return length == 0;
    }

    @Override
    public Slot query(Object key, int index) {
        Object k = makeKey(key, index);
        int found = shape.get(k);
        if (found < 0) {
            return null;
        }
        assert found < length;
        return slots[found];
    }

    @Override
    public Slot modify(SlotMapOwner owner, Object key, int index, int attributes) {
        Object k = makeKey(key, index);
        var r = shape.putIfAbsent(k);
        if (!r.isNewShape()) {
            return slots[r.getIndex()];
        }
        assert r.getIndex() == length;
        Slot newSlot = new Slot(key, index, attributes);
        if (length == MAXIMUM_SIZE) {
            promoteMap(owner, newSlot);
            return newSlot;
        }

        ensureMoreCapacity();
        slots[length++] = newSlot;
        shape = r.getShape();
        return newSlot;
    }

    @Override
    public <S extends Slot> S compute(
            SlotMapOwner owner, Object key, int index, SlotComputer<S> compute) {
        Object k = makeKey(key, index);
        var r = shape.putIfAbsent(k);
        if (r.isNewShape()) {
            assert r.getIndex() == length;
            return computeNew(owner, key, index, r.getShape(), compute);
        } else {
            return computeExisting(owner, key, index, slots[r.getIndex()], r.getIndex(), compute);
        }
    }

    private <S extends Slot> S computeExisting(
            SlotMapOwner owner,
            Object key,
            int index,
            Slot slot,
            int slotIndex,
            SlotComputer<S> compute) {
        S result = compute.compute(key, index, slot);
        if (result == null) {
            // We do not support delete, so promote to a slot map that can
            promoteMap(owner, null);
            owner.getMap().compute(owner, key, index, (k, i, s) -> null);
        } else {
            assert slotIndex < length;
            slots[slotIndex] = result;
        }
        return result;
    }

    private <S extends Slot> S computeNew(
            SlotMapOwner owner, Object key, int index, Shape newShape, SlotComputer<S> compute) {
        S newSlot = compute.compute(key, index, null);
        if (newSlot != null) {
            if (length == MAXIMUM_SIZE) {
                promoteMap(owner, newSlot);
            } else {
                ensureMoreCapacity();
                slots[length++] = newSlot;
                shape = newShape;
            }
        }
        return newSlot;
    }

    @Override
    public void add(SlotMapOwner owner, Slot newSlot) {
        Object k = makeKey(newSlot.name, newSlot.indexOrHash);
        var r = shape.putIfAbsent(k);
        assert r.isNewShape();
        assert r.getIndex() == length;
        if (length == MAXIMUM_SIZE) {
            promoteMap(owner, newSlot);
        } else {
            ensureMoreCapacity();
            slots[length++] = newSlot;
            shape = r.getShape();
        }
    }

    private void ensureMoreCapacity() {
        if (length + 1 == slots.length) {
            Slot[] newSlots = new Slot[slots.length * 2];
            System.arraycopy(slots, 0, newSlots, 0, slots.length);
            slots = newSlots;
        }
    }

    private void promoteMap(SlotMapOwner owner, Slot newSlot) {
        // TODO embedded map instead?
        if (newSlot == null) {
            if (owner != null) {
                owner.setMap(new HashSlotMap(this));
            } else {
                // This should only happen in tests
                throw new AssertionError("No slot map can support a delete here");
            }
        } else if (owner != null) {
            owner.setMap(new HashSlotMap(this, newSlot));
        }
    }

    @Override
    public Iterator<Slot> iterator() {
        return new Iter();
    }

    private static Object makeKey(Object name, int index) {
        return name == null ? index : name;
    }

    private final class Iter implements Iterator<Slot> {
        private int pos;

        @Override
        public boolean hasNext() {
            return pos < length;
        }

        @Override
        public Slot next() {
            return slots[pos++];
        }
    }
}
