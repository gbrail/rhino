package org.mozilla.javascript;

/**
 * This is a specialization of Slot to store values that are retrieved via calls to script
 * functions. It's used to load built-in objects more efficiently.
 */
public class LambdaLazyLoadSlot extends Slot {
    private enum State {
        NEW,
        INITIALIZING,
        INITIALIZED
    }

    private transient java.util.function.Function<Context, Object> loader;
    private transient State state = State.NEW;

    LambdaLazyLoadSlot(Object name, int index) {
        super(name, index, 0);
    }

    LambdaLazyLoadSlot(Slot oldSlot) {
        super(oldSlot);
    }

    public void setLoader(java.util.function.Function<Context, Object> l) {
        this.loader = l;
        this.state = State.NEW;
        this.value = Scriptable.NOT_FOUND;
    }

    @Override
    LambdaLazyLoadSlot copySlot() {
        var newSlot = new LambdaLazyLoadSlot(this);
        newSlot.value = value;
        newSlot.next = null;
        newSlot.orderedNext = null;
        return newSlot;
    }

    @Override
    public Object getValue(Scriptable start) {
        switch (state) {
            case NEW:
                {
                    try {
                        state = State.INITIALIZING;
                        value = loader.apply(Context.getCurrentContext());
                    } catch (RecursiveLoadingException re) {
                        // Ignore, because in our recursive loading situations
                        // we already assigned "value" before this was thrown!
                    } finally {
                        state = State.INITIALIZED;
                        loader = null;
                    }
                    return value;
                }
            case INITIALIZING:
                // In some parts of the codebase, recursive loading happens.
                // Bail out early in that case or else we will recurse forever.
                throw new RecursiveLoadingException();
            case INITIALIZED:
                return value;
            default:
                throw Kit.codeBug();
        }
    }

    private static final class RecursiveLoadingException extends RuntimeException {}
}
