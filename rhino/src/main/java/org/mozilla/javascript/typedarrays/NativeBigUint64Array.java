/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.typedarrays;

import static org.mozilla.javascript.ClassDescriptor.Builder.value;
import static org.mozilla.javascript.ClassDescriptor.Destination.CTOR;
import static org.mozilla.javascript.ClassDescriptor.Destination.PROTO;

import java.io.Serial;
import java.math.BigInteger;
import org.mozilla.javascript.ClassDescriptor;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.JSFunction;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.ScriptRuntimeES6;
import org.mozilla.javascript.SymbolKey;
import org.mozilla.javascript.TopLevel;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.VarScope;

/**
 * An array view that stores 64-bit quantities and implements the JavaScript "Float64Array"
 * interface. It also implements List&lt;Double&gt; for direct manipulation in Java.
 */
public class NativeBigUint64Array extends NativeBigIntArrayView {
    @Serial private static final long serialVersionUID = 6278562787625694949L;

    private static final String CLASS_NAME = "BigUint64Array";
    private static final int BYTES_PER_ELEMENT = 8;

    private static final ClassDescriptor DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(
                                CLASS_NAME,
                                3,
                                NativeTypedArrayView::typeError,
                                NativeBigUint64Array::js_constructor)
                        .withProp(CTOR, "BYTES_PER_ELEMENT", value(8))
                        .withProp(PROTO, "BYTES_PER_ELEMENT", value(8))
                        .withProp(CTOR, SymbolKey.SPECIES, ScriptRuntimeES6::symbolSpecies)
                        .build();
    }

    public NativeBigUint64Array() {}

    public NativeBigUint64Array(NativeArrayBuffer ab, int off, int len) {
        super(ab, off, len, len * BYTES_PER_ELEMENT);
    }

    public NativeBigUint64Array(int len) {
        this(new NativeArrayBuffer((double) len * BYTES_PER_ELEMENT), 0, len);
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    public static JSFunction init(Context cx, VarScope scope, boolean sealed) {
        return NativeTypedArrayView.initSubClass(cx, scope, DESCRIPTOR, sealed);
    }

    @Override
    public int getBytesPerElement() {
        return BYTES_PER_ELEMENT;
    }

    private static Object js_constructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return NativeTypedArrayView.js_constructor(
                cx,
                f,
                nt,
                s,
                thisObj,
                args,
                NativeBigUint64Array::new,
                8,
                TopLevel.Builtins.BigUint64Array);
    }

    @Override
    protected Object js_get(int index) {
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        long base = (long) accessor.get(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset);
        return Conversions.longBitsToBigUint(base);
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
    public BigInteger get(int i) {
        if (checkIndex(i)) {
            throw new IndexOutOfBoundsException();
        }
        return (BigInteger) js_get(i);
    }

    @Override
    public BigInteger set(int i, BigInteger aByte) {
        if (checkIndex(i)) {
            throw new IndexOutOfBoundsException();
        }
        return (BigInteger) js_set(i, aByte);
    }

    @Override
    public Object atomicLoad(int index) {
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.getVolatile(
                                arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset);
        return Conversions.longBitsToBigUint(base);
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
        return Conversions.longBitsToBigUint(base);
    }

    @Override
    public Object atomicSub(int index, Object v) {
        long val = ScriptRuntime.toBigInt(v).longValue();
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.getAndAdd(
                                arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, -val);
        return Conversions.longBitsToBigUint(base);
    }

    @Override
    public Object atomicAnd(int index, Object v) {
        long val = ScriptRuntime.toBigInt(v).longValue();
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.getAndBitwiseAnd(
                                arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
        return Conversions.longBitsToBigUint(base);
    }

    @Override
    public Object atomicOr(int index, Object v) {
        long val = ScriptRuntime.toBigInt(v).longValue();
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.getAndBitwiseOr(
                                arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
        return Conversions.longBitsToBigUint(base);
    }

    @Override
    public Object atomicXor(int index, Object v) {
        long val = ScriptRuntime.toBigInt(v).longValue();
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.getAndBitwiseXor(
                                arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
        return Conversions.longBitsToBigUint(base);
    }

    @Override
    public Object atomicExchange(int index, Object v) {
        long val = ScriptRuntime.toBigInt(v).longValue();
        checkAtomicIndex(index);
        long base =
                (long)
                        accessor.getAndSet(
                                arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
        return Conversions.longBitsToBigUint(base);
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
        return Conversions.longBitsToBigUint(base);
    }
}
