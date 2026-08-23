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
import java.util.function.BiFunction;
import org.mozilla.javascript.ClassDescriptor;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.JSFunction;
import org.mozilla.javascript.ScriptRuntimeES6;
import org.mozilla.javascript.SymbolKey;
import org.mozilla.javascript.TopLevel;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.VarScope;

/**
 * An array view that stores 32-bit quantities and implements the JavaScript "Uint32Array"
 * interface. It also implements List&lt;Long&gt; for direct manipulation in Java.
 */
public class NativeUint32Array extends NativeTypedArrayView<Long> implements AtomicSupport {
    @Serial private static final long serialVersionUID = -7987831421954144244L;

    private static final String CLASS_NAME = "Uint32Array";
    protected static final int BYTES_PER_ELEMENT = 4;

    private static final ClassDescriptor DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(
                                CLASS_NAME,
                                3,
                                NativeTypedArrayView::typeError,
                                NativeUint32Array::js_constructor)
                        .withProp(CTOR, "BYTES_PER_ELEMENT", value(4))
                        .withProp(PROTO, "BYTES_PER_ELEMENT", value(4))
                        .withProp(CTOR, SymbolKey.SPECIES, ScriptRuntimeES6::symbolSpecies)
                        .build();
    }

    public NativeUint32Array() {
        super(Integer.TYPE);
    }

    public NativeUint32Array(NativeArrayBuffer ab, int off, int len) {
        super(Integer.TYPE, ab, off, len, len * BYTES_PER_ELEMENT);
    }

    public NativeUint32Array(int len) {
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
                shared ? NativeSharedUint32Array::new : NativeUint32Array::new,
                4,
                TopLevel.Builtins.Uint32Array);
    }

    @Override
    protected Object js_get(int index) {
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        int intBits = arrayBuffer.buffer.getInt((index * BYTES_PER_ELEMENT) + offset);
        return Conversions.intBitsToUint(intBits);
    }

    @Override
    protected Object js_set(int index, Object c) {
        int val = Conversions.toUint32(c);
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        arrayBuffer.buffer.putInt((index * BYTES_PER_ELEMENT) + offset, val);
        return null;
    }

    @Override
    public Long get(int i) {
        ensureIndex(i);
        return (Long) js_get(i);
    }

    @Override
    public Long set(int i, Long aByte) {
        ensureIndex(i);
        return (Long) js_set(i, aByte);
    }

    @Override
    public Object atomicLoad(int index) {
        checkAtomicIndex(index);
        int base;
        synchronized (arrayBuffer) {
            base = arrayBuffer.buffer.getInt((index * BYTES_PER_ELEMENT) + offset);
        }
        return Conversions.intBitsToUint(base);
    }

    @Override
    public Object atomicStore(int index, Object v) {
        double num = coerceNumber(v);
        int val = Conversions.toUint32(num);
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            arrayBuffer.buffer.putInt((index * BYTES_PER_ELEMENT) + offset, val);
        }
        return num;
    }

    private Object mathOp(int index, Object v, BiFunction<Integer, Integer, Integer> f) {
        int val = Conversions.toUint32(v);
        checkAtomicIndex(index);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        synchronized (arrayBuffer) {
            int old = arrayBuffer.buffer.getInt(addr);
            int r = f.apply(old, val);
            arrayBuffer.buffer.putInt(addr, r);
            return Conversions.intBitsToUint(old);
        }
    }

    @Override
    public Object atomicAdd(int index, Object v) {
        return mathOp(index, v, Integer::sum);
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
        int val = Conversions.toUint32(v);
        checkAtomicIndex(index);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        synchronized (arrayBuffer) {
            int old = arrayBuffer.buffer.getInt(addr);
            arrayBuffer.buffer.putInt(addr, val);
            return Conversions.intBitsToUint(old);
        }
    }

    @Override
    public Object atomicCompareAndExchange(int index, Object e, Object r) {
        int expected = Conversions.toUint32(e);
        int replacement = Conversions.toUint32(r);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            int old = arrayBuffer.buffer.getInt(addr);
            if (old == expected) {
                arrayBuffer.buffer.putInt(addr, replacement);
            }
            return Conversions.intBitsToUint(old);
        }
    }
}
