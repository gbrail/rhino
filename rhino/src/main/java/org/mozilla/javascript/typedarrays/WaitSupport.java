package org.mozilla.javascript.typedarrays;

/** Typed array implementations that support "wait" and "notify" implement this interface. */
public interface WaitSupport {
    boolean isShared();

    boolean isDetached();

    Object wait(int index, Object val, Object timeout);

    Object waitAsync(int index, Object val, Object timeout);

    /**
     * Wakes up to {@code count} waiters at {@code index}; the buffer must be shared and attached.
     */
    Object notify(int index, int count);
}
