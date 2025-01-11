package org.mozilla.javascript;

/**
 * This is an older lazy loading mechanism used with reflection-based constructors that's around for
 * backward compatibility.
 */
public class LazyLoadSlot extends Slot {
    LazyLoadSlot(Object name, int index) {
        super(name, index, 0);
    }

    LazyLoadSlot(Slot oldSlot) {
        super(oldSlot);
    }

    @Override
    LazyLoadSlot copySlot() {
        var newSlot = new LazyLoadSlot(this);
        newSlot.value = value;
        newSlot.next = null;
        newSlot.orderedNext = null;
        return newSlot;
    }

    @Override
    public Object getValue(Scriptable start) {
        Object val = this.value;
        if (val instanceof LazilyLoadedCtor) {
            LazilyLoadedCtor initializer = (LazilyLoadedCtor) val;
            try {
                initializer.init();
            } finally {
                this.value = val = initializer.getValue();
            }
        }
        return val;
    }
}
