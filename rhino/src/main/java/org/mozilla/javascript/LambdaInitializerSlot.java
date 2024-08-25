package org.mozilla.javascript;

import java.util.function.BiFunction;

class LambdaInitializerSlot extends Slot {
    private transient BiFunction<Context, Scriptable, Object> initializer;
    private boolean initialized = false;

    LambdaInitializerSlot(Object name, int index, int attributes) {
        super(name, index, attributes);
    }

    LambdaInitializerSlot(Slot existing) {
        super(existing);
    }

    void setInitializer(BiFunction<Context, Scriptable, Object> i) {
        this.initializer = i;
    }

    void initialize(Scriptable start) {
        Context cx = Context.getCurrentContext();
        setValue(initializer.apply(cx, start), start, start);
        initialized = true;
    }

    @Override
    public Object getValue(Scriptable start) {
        if (!initialized) {
            initialize(start);
        }
        return super.getValue(start);
    }
}
