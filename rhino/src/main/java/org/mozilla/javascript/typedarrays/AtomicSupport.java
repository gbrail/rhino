package org.mozilla.javascript.typedarrays;

/**
 * Typed array implementations that can support atomic operations according to the spec implement
 * this interface. Different implementations may use actual atomic instructions, or they may use
 * traditional Java synchronization, or other mechanisms.
 */
public interface AtomicSupport {
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
