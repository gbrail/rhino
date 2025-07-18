package org.mozilla.javascript;

import java.util.Iterator;
import java.util.Objects;

public class ShapedSlotMap implements SlotMap {
    private static final int INITIAL_LENGTH = 8;
    // The value of this type of slot map tapers off with larger objects
    public static final int MAXIMUM_SIZE = 32;

    private Slot[] slots;
    private int length;
    private Shape shape;

    public ShapedSlotMap(Context cx) {
        slots = new Slot[INITIAL_LENGTH];
        // Support null contexts for tests only
        shape = cx == null ? new Shape() : cx.getRootShape();
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
        if (key == null) {
            // We do not support indexed keys
            return null;
        }
        int found = shape.get(key);
        if (found < 0) {
            return null;
        }
        assert found < length;
        return slots[found];
    }

    @Override
    public SlotMap.Key getFastQueryKey(Object key) {
        if (key == null) {
            // We do not support indexed keys
            return null;
        }
        int found = shape.get(key);
        assert found < length;
        if (found >= 0) {
            return new QueryKey(shape, found);
        }
        return null;
    }

    @Override
    public Slot queryFast(SlotMap.Key key) {
        assert key instanceof QueryKey;
        return slots[((QueryKey) key).index];
    }

    @Override
    public Slot modify(SlotMapOwner owner, Object key, int index, int attributes) {
        if (key == null) {
            // We do not support integer keys
            promoteMap(owner, null);
            return owner.getMap().modify(owner, key, index, attributes);
        }
        var r = shape.putIfAbsent(key);
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
    public SlotMap.Key getFastModifyKey(Object key, int attributes, boolean isExtensible) {
        if (key == null) {
            // We do not support indexed keys
            return null;
        }
        var r = shape.putIfAbsent(key);
        if (!r.isNewShape()) {
            // Key may only be used to modify an existing property
            return new ModifyKey(shape, r.getIndex());
        }
        if (length == MAXIMUM_SIZE || !isExtensible) {
            // Does not work if object is not extensible, and also
            // support the edge case of a map that is about to be promoted
            // to a non-shaped map.
            return null;
        }
        // A key that represents a new property
        return new ExtendKey(key, attributes, shape, r.getShape(), r.getIndex());
    }

    @Override
    public Slot modifyFast(SlotMap.Key key) {
        if (key instanceof ModifyKey) {
            return queryFast(key);
        }
        assert key instanceof ExtendKey;
        ExtendKey m = (ExtendKey) key;
        assert m.index == length;
        assert m.index < MAXIMUM_SIZE;
        Slot newSlot = new Slot(m.key, 0, m.attributes);
        ensureMoreCapacity();
        slots[length++] = newSlot;
        shape = m.successorShape;
        return newSlot;
    }

    @Override
    public <S extends Slot> S compute(
            SlotMapOwner owner, Object key, int index, SlotComputer<S> compute) {
        if (key == null) {
            // We do not support integer keys
            promoteMap(owner, null);
            return owner.getMap().compute(owner, key, index, compute);
        }
        var r = shape.putIfAbsent(key);
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
        if (newSlot.name == null) {
            // We do not support integer keys
            promoteMap(owner, newSlot);
            return;
        }
        var r = shape.putIfAbsent(newSlot.name);
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

    @Override
    public SlotMap.Key getFastWildcardKey() {
        return new QueryKey(shape, 0);
    }

    private void ensureMoreCapacity() {
        if (length + 1 == slots.length) {
            Slot[] newSlots = new Slot[slots.length * 2];
            System.arraycopy(slots, 0, newSlots, 0, slots.length);
            slots = newSlots;
        }
    }

    private void promoteMap(SlotMapOwner owner, Slot newSlot) {
        if (newSlot == null) {
            if (owner != null) {
                owner.setMap(new EmbeddedSlotMap(this));
            } else {
                // This should only happen in tests
                throw new AssertionError("No slot map can support a delete here");
            }
        } else if (owner != null) {
            owner.setMap(new EmbeddedSlotMap(this, newSlot));
        }
    }

    @Override
    public Iterator<Slot> iterator() {
        return new Iter();
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

    private static class QueryKey implements SlotMap.Key {
        private final Shape shape;
        private final int index;

        QueryKey(Shape shape, int index) {
            this.shape = shape;
            this.index = index;
        }

        @Override
        public boolean isCompatible(SlotMap m) {
            if (m instanceof ShapedSlotMap) {
                return Objects.equals(shape, ((ShapedSlotMap) m).shape);
            }
            return false;
        }
    }

    private static final class ModifyKey extends QueryKey {
        ModifyKey(Shape shape, int index) {
            super(shape, index);
        }
    }

    private static final class ExtendKey implements SlotMap.Key {
        private final Object key;
        private final int attributes;
        private final Shape shape;
        private final Shape successorShape;
        private final int index;

        ExtendKey(Object key, int attributes, Shape shape, Shape successorShape, int index) {
            this.key = key;
            this.attributes = attributes;
            this.shape = shape;
            this.successorShape = successorShape;
            this.index = index;
        }

        @Override
        public boolean isCompatible(SlotMap m) {
            if (m instanceof ShapedSlotMap) {
                return Objects.equals(shape, ((ShapedSlotMap) m).shape);
            }
            return false;
        }

        @Override
        public boolean isExtending() {
            return true;
        }
    }
}
