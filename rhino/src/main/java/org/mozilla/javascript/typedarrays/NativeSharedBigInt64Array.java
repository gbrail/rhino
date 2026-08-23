/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.typedarrays;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.math.BigInteger;
import java.nio.ByteOrder;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Undefined;

public class NativeSharedBigInt64Array extends NativeBigInt64Array {
    private static final VarHandle handleLE =
            MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle handleBE =
            MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    private final VarHandle accessor;

    public NativeSharedBigInt64Array(NativeArrayBuffer ab, int off, int len) {
        super(ab, off, len);
        if (ab.buffer.order() == ByteOrder.BIG_ENDIAN) {
            accessor = handleBE;
        } else {
            accessor = handleLE;
        }
    }

    @Override
    protected Object js_get(int index) {
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        long base = (long) accessor.get(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset);
        return BigInteger.valueOf(base);
    }

    @Override
    protected Object js_set(int index, Object c) {
        var val = ScriptRuntime.toBigInt(c);
        if (checkIndex(index)) {
            return Undefined.instance;
        }

        long base = val.longValue();
        accessor.set(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, base);
        return null;
    }

    @Override
    public Object atomicLoad(int index) {
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.getVolatile(
                                arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset);
        return BigInteger.valueOf(base);
    }

    @Override
    public Object atomicStore(int index, Object v) {
        var val = ScriptRuntime.toBigInt(v);
        long base = val.longValue();
        checkAtomicIndex(index);
        accessor.setVolatile(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, base);
        return val;
    }

    @Override
    public Object atomicAdd(int index, Object v) {
        long val = ScriptRuntime.toBigInt(v).longValue();
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.getAndAdd(
                                arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
        return BigInteger.valueOf(base);
    }

    @Override
    public Object atomicSub(int index, Object v) {
        long val = ScriptRuntime.toBigInt(v).longValue();
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.getAndAdd(
                                arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, -val);
        return BigInteger.valueOf(base);
    }

    @Override
    public Object atomicAnd(int index, Object v) {
        long val = ScriptRuntime.toBigInt(v).longValue();
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.getAndBitwiseAnd(
                                arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
        return BigInteger.valueOf(base);
    }

    @Override
    public Object atomicOr(int index, Object v) {
        long val = ScriptRuntime.toBigInt(v).longValue();
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.getAndBitwiseOr(
                                arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
        return BigInteger.valueOf(base);
    }

    @Override
    public Object atomicXor(int index, Object v) {
        long val = ScriptRuntime.toBigInt(v).longValue();
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.getAndBitwiseXor(
                                arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
        return BigInteger.valueOf(base);
    }

    @Override
    public Object atomicExchange(int index, Object v) {
        long val = ScriptRuntime.toBigInt(v).longValue();
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.getAndSet(
                                arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
        return BigInteger.valueOf(base);
    }

    @Override
    public Object atomicCompareAndExchange(int index, Object e, Object r) {
        long expected = ScriptRuntime.toBigInt(e).longValue();
        long replacement = ScriptRuntime.toBigInt(r).longValue();
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.compareAndExchange(
                                arrayBuffer.buffer,
                                (index * BYTES_PER_ELEMENT) + offset,
                                expected,
                                replacement);
        return BigInteger.valueOf(base);
    }

    @Override
    protected long readCurrent(int index) {
        return (long)
                accessor.getVolatile(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset);
    }
}
