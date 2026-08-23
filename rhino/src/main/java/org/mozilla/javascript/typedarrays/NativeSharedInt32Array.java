/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.typedarrays;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Undefined;

/**
 * This array supports atomic and regular operations on an Int32 array for a shared array buffer.
 */
public class NativeSharedInt32Array extends NativeInt32Array {
    private static final VarHandle handleLE =
            MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle handleBE =
            MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

    private final VarHandle accessor;

    public NativeSharedInt32Array(NativeArrayBuffer ab, int off, int len) {
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
        // The accessor might be faster, and supports "no tear" access on a shared buffer
        return (int) accessor.get(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset);
    }

    @Override
    protected Object js_set(int index, Object c) {
        int val = ScriptRuntime.toInt32(c);
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        accessor.set(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
        return null;
    }

    @Override
    public Object atomicLoad(int index) {
        checkAtomicIndex(index);
        return (int) accessor.getVolatile(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset);
    }

    @Override
    public Object atomicStore(int index, Object v) {
        double num = coerceNumber(v);
        int val = ScriptRuntime.toInt32(num);
        checkAtomicIndex(index);
        accessor.setVolatile(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
        return num;
    }

    @Override
    public Object atomicAdd(int index, Object v) {
        int val = ScriptRuntime.toInt32(v);
        checkAtomicIndex(index);
        return (int)
                accessor.getAndAdd(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
    }

    @Override
    public Object atomicSub(int index, Object v) {
        int val = ScriptRuntime.toInt32(v);
        checkAtomicIndex(index);
        return (int)
                accessor.getAndAdd(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, -val);
    }

    @Override
    public Object atomicAnd(int index, Object v) {
        int val = ScriptRuntime.toInt32(v);
        checkAtomicIndex(index);
        return (int)
                accessor.getAndBitwiseAnd(
                        arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
    }

    @Override
    public Object atomicOr(int index, Object v) {
        int val = ScriptRuntime.toInt32(v);
        checkAtomicIndex(index);
        return (int)
                accessor.getAndBitwiseOr(
                        arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
    }

    @Override
    public Object atomicXor(int index, Object v) {
        int val = ScriptRuntime.toInt32(v);
        checkAtomicIndex(index);
        return (int)
                accessor.getAndBitwiseXor(
                        arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
    }

    @Override
    public Object atomicExchange(int index, Object v) {
        int val = ScriptRuntime.toInt32(v);
        checkAtomicIndex(index);
        return (int)
                accessor.getAndSet(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
    }

    @Override
    public Object atomicCompareAndExchange(int index, Object e, Object r) {
        int expected = ScriptRuntime.toInt32(e);
        int replacement = ScriptRuntime.toInt32(r);
        checkAtomicIndex(index);
        return (int)
                accessor.compareAndExchange(
                        arrayBuffer.buffer,
                        (index * BYTES_PER_ELEMENT) + offset,
                        expected,
                        replacement);
    }

    @Override
    protected int readCurrent(int index) {
        return (int) accessor.getVolatile(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset);
    }
}
