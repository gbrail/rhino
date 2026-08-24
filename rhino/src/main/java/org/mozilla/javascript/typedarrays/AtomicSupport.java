package org.mozilla.javascript.typedarrays;

/**
 * Typed array implementations that can support atomic operations according to the spec implement
 * this interface. Different implementations may use actual atomic instructions, or they may use
 * traditional Java synchronization, or other mechanisms.
 */
public interface AtomicSupport {
    /** If true, backed by a shared ArrayBuffer */
    boolean isShared();

    /** If true, backing buffer has been detached */
    boolean isDetached();

    /**
     * If true, type is capable of "wait" operations, but may not be implemented if the type is not
     * shared. WaitSupport will be implemented if "wait" is actually supported. This basically tells
     * us if "notify" may be called.
     */
    default boolean isWaitCapable() {
        return false;
    }

    Object atomicLoad(int index);

    Object atomicStore(int index, Object val);

    Object atomicAdd(int index, Object val);

    Object atomicSub(int index, Object val);

    Object atomicAnd(int index, Object val);

    Object atomicOr(int index, Object val);

    Object atomicXor(int index, Object val);

    Object atomicExchange(int index, Object val);

    Object atomicCompareAndExchange(int index, Object expected, Object replacement);
}
