package org.mozilla.javascript.typedarrays;

/** Typed array implementations that support "wait" and "notify" implement this interface. */
public interface WaitSupport {
    boolean isShared();

    Object wait(int index, Object val, Object timeout);

    Object waitAsync(int index, Object val, Object timeout);

    Object notify(int index, int count);
}
