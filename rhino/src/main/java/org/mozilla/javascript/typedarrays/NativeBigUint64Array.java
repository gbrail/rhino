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
import java.util.function.BiFunction;
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
 * An array view that stores 64-bit quantities and implements the JavaScript "BigUint64Array"
 * interface. It also implements List&lt;Double&gt; for direct manipulation in Java.
 */
public class NativeBigUint64Array extends NativeBigIntArrayView implements AtomicSupport {
    @Serial private static final long serialVersionUID = 6278562787625694949L;

    private static final String CLASS_NAME = "BigUint64Array";
    protected static final int BYTES_PER_ELEMENT = 8;

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
        // Pre-inspect the array buffer and see if it's shared
        boolean shared =
                (args.length > 0 && args[0] instanceof NativeArrayBuffer ab && ab.isShared());
        return NativeTypedArrayView.js_constructor(
                cx,
                f,
                nt,
                s,
                thisObj,
                args,
                shared ? NativeSharedBigUint64Array::new : NativeBigUint64Array::new,
                8,
                TopLevel.Builtins.BigUint64Array);
    }

    @Override
    protected Object js_get(int index) {
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        long base = arrayBuffer.buffer.getLong((index * BYTES_PER_ELEMENT) + offset);
        return Conversions.longBitsToBigUint(base);
    }

    @Override
    protected Object js_set(int index, Object c) {
        var val = ScriptRuntime.toBigInt(c);
        if (checkIndex(index)) {
            return Undefined.instance;
        }

        long base = val.longValue();
        arrayBuffer.buffer.putLong((index * BYTES_PER_ELEMENT) + offset, base);
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
        long base;
        synchronized (arrayBuffer) {
            base = arrayBuffer.buffer.getLong((index * BYTES_PER_ELEMENT) + offset);
        }
        return Conversions.longBitsToBigUint(base);
    }

    @Override
    public Object atomicStore(int index, Object v) {
        var val = ScriptRuntime.toBigInt(v);
        long base = val.longValue();
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            arrayBuffer.buffer.putLong((index * BYTES_PER_ELEMENT) + offset, base);
        }
        return val;
    }

    private Object mathOp(int index, Object v, BiFunction<Long, Long, Long> f) {
        var bVal = ScriptRuntime.toBigInt(v);
        long val = bVal.longValue();
        checkAtomicIndex(index);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        synchronized (arrayBuffer) {
            long old = arrayBuffer.buffer.getLong(addr);
            long r = f.apply(old, val);
            arrayBuffer.buffer.putLong(addr, r);
            return Conversions.longBitsToBigUint(old);
        }
    }

    @Override
    public Object atomicAdd(int index, Object v) {
        return mathOp(index, v, Long::sum);
    }

    @Override
    public Object atomicSub(int index, Object v) {
        return mathOp(index, v, (a, b) -> a - b);
    }

    @Override
    public Object atomicAnd(int index, Object v) {
        return mathOp(index, v, (a, b) -> a & b);
    }

    @Override
    public Object atomicOr(int index, Object v) {
        return mathOp(index, v, (a, b) -> a | b);
    }

    @Override
    public Object atomicXor(int index, Object v) {
        return mathOp(index, v, (a, b) -> a ^ b);
    }

    @Override
    public Object atomicExchange(int index, Object v) {
        long val = ScriptRuntime.toBigInt(v).longValue();
        checkAtomicIndex(index);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        synchronized (arrayBuffer) {
            long old = arrayBuffer.buffer.getLong(addr);
            arrayBuffer.buffer.putLong(addr, val);
            return Conversions.longBitsToBigUint(old);
        }
    }

    @Override
    public Object atomicCompareAndExchange(int index, Object e, Object r) {
        long expected = ScriptRuntime.toBigInt(e).longValue();
        long replacement = ScriptRuntime.toBigInt(r).longValue();
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            long old = arrayBuffer.buffer.getLong(addr);
            if (old == expected) {
                arrayBuffer.buffer.putLong(addr, replacement);
            }
            return Conversions.longBitsToBigUint(old);
        }
    }
}
