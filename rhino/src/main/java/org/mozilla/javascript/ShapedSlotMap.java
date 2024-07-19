package org.mozilla.javascript;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.OptionalInt;

public class ShapedSlotMap implements SlotMap {

    private ObjectShape shape;
    private final ArrayList<Slot> slots = new ArrayList<>();

    public ShapedSlotMap() {
        // For there to be any possibility of optimization, we need to get the root
        // shape from the current context. But fall back so that tests work.
        Context cx = Context.getCurrentContext();
        if (cx == null) {
            shape = new ObjectShape();
        } else {
            shape = cx.getRootShape();
        }
    }

    @Override
    public int size() {
        return slots.size();
    }

    @Override
    public boolean isEmpty() {
        return slots.isEmpty();
    }

    @Override
    public Slot query(Object name, int index) {
        Object key = makeKey(name, index);
        OptionalInt location = shape.getProperty(key);
        if (location.isPresent()) {
            return slots.get(location.getAsInt());
        }
        return null;
    }

    public OptionalInt queryFastIndex(Object name, int index) {
        Object key = makeKey(name, index);
        return shape.getProperty(key);
    }

    public Slot queryFast(int fastIndex) {
        return slots.get(fastIndex);
    }

    @Override
    public Slot modify(Object name, int index, int attributes) {
        Object key = makeKey(name, index);
        ObjectShape.Result r = shape.putProperty(key);
        if (r.getShape().isPresent()) {
            // New property, with a new shape
            assert (slots.size() == r.getIndex());
            Slot newSlot = new Slot(name, index, attributes);
            slots.add(newSlot);
            shape = r.getShape().get();
            return newSlot;
        }
        return slots.get(r.getIndex());
    }

    @Override
    public void add(Slot newSlot) {
        Object key = makeKey(newSlot);
        ObjectShape.Result r = shape.putProperty(key);
        // This is an unconditional add, only used when deserializing
        assert r.getShape().isPresent();
        assert (slots.size() == r.getIndex());
        slots.add(newSlot);
        shape = r.getShape().get();
    }

    @Override
    public <S extends Slot> S compute(Object name, int index, SlotComputer<S> c) {
        Object key = makeKey(name, index);
        ObjectShape.Result r = shape.putProperty(key);

        if (r.getShape().isPresent()) {
            // New property, with a new shape
            S newSlot = c.compute(name, index, null);
            if (newSlot == null) {
                // Never mind...
                return null;
            }
            assert (slots.size() == r.getIndex());
            slots.add(newSlot);
            shape = r.getShape().get();
            return newSlot;
        }

        Slot existingSlot = slots.get(r.getIndex());
        S newSlot = c.compute(name, index, existingSlot);
        if (newSlot == null) {
            // This is actually a remove now, which is complicated
            removeSlot(r.getIndex());
        } else if (!Objects.equals(existingSlot, newSlot)) {
            slots.set(r.getIndex(), newSlot);
        }
        return newSlot;
    }

    /**
     * Because we don't know what will happen in the future, we must process a removal by traversing
     * the object shape tree upwards, then re-filling it with the new keys. This is a bit expensive
     * but removals are rare.
     */
    private void removeSlot(int index) {
        // Build a stack of slots that come after the one to remove
        ArrayDeque<Slot> removedSlots = new ArrayDeque<>();
        for (int i = slots.size() - 1; i > index; i--) {
            removedSlots.addLast(slots.remove(i));
            shape = shape.getParent();
        }
        // Actually remove the last slot
        assert (slots.size() == index + 1);
        slots.remove(index);
        shape = shape.getParent();
        // Replace the other slots in order
        Slot replacedSlot;
        do {
            replacedSlot = removedSlots.pollLast();
            if (replacedSlot != null) {
                add(replacedSlot);
            }
        } while (replacedSlot != null);
    }

    @Override
    public Iterator<Slot> iterator() {
        return slots.iterator();
    }

    private static Object makeKey(Object name, int index) {
        return name == null ? index : name;
    }

    private static Object makeKey(Slot slot) {
        return makeKey(slot.name, slot.indexOrHash);
    }
}
