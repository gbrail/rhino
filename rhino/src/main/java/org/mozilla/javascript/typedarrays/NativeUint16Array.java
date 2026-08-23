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
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.ScriptRuntimeES6;
import org.mozilla.javascript.SymbolKey;
import org.mozilla.javascript.TopLevel;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.VarScope;

/**
 * An array view that stores 16-bit quantities and implements the JavaScript "Uint16Array"
 * interface. It also implements List&lt;Integer&gt; for direct manipulation in Java.
 */
public class NativeUint16Array extends NativeTypedArrayView<Integer> implements AtomicSupport {
    @Serial private static final long serialVersionUID = 7700018949434240321L;

    private static final String CLASS_NAME = "Uint16Array";
    private static final int BYTES_PER_ELEMENT = 2;

    private static final ClassDescriptor DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(
                                CLASS_NAME,
                                3,
                                NativeTypedArrayView::typeError,
                                NativeUint16Array::js_constructor)
                        .withProp(CTOR, "BYTES_PER_ELEMENT", value(2))
                        .withProp(PROTO, "BYTES_PER_ELEMENT", value(2))
                        .withProp(CTOR, SymbolKey.SPECIES, ScriptRuntimeES6::symbolSpecies)
                        .build();
    }

    public NativeUint16Array() {
        super(Short.TYPE);
    }

    public NativeUint16Array(NativeArrayBuffer ab, int off, int len) {
        super(Short.TYPE, ab, off, len, len * BYTES_PER_ELEMENT);
    }

    public NativeUint16Array(int len) {
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
                NativeUint16Array::new,
                2,
                TopLevel.Builtins.Uint16Array);
    }

    @Override
    protected Object js_get(int index) {
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        short shortBits = arrayBuffer.buffer.getShort((index * BYTES_PER_ELEMENT) + offset);
        return Conversions.shortBitsToUint(shortBits);
    }

    @Override
    protected Object js_set(int index, Object c) {
        short val = Conversions.toUint16(c);
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        arrayBuffer.buffer.putShort((index * BYTES_PER_ELEMENT) + offset, val);
        return null;
    }

    @Override
    public Integer get(int i) {
        ensureIndex(i);
        return (Integer) js_get(i);
    }

    @Override
    public Integer set(int i, Integer aByte) {
        ensureIndex(i);
        return (Integer) js_set(i, aByte);
    }

    // In Java 17, only load and store can be atomic.
    // Others are going to require explicit locking.

    @Override
    public Object atomicLoad(int index) {
        checkAtomicIndex(index);
        short bits;
        synchronized (arrayBuffer) {
            bits = arrayBuffer.buffer.getShort((index * BYTES_PER_ELEMENT) + offset);
        }
        return Conversions.shortBitsToUint(bits);
    }

    @Override
    public Object atomicStore(int index, Object v) {
        double num = coerceNumber(v);
        short val = (short) (ScriptRuntime.toInt32(num) & 0xffff);
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            arrayBuffer.buffer.putShort((index * BYTES_PER_ELEMENT) + offset, val);
        }
        return num;
    }

    private Object mathOp(int index, Object v, BiFunction<Integer, Integer, Integer> f) {
        short val = Conversions.toUint16(v);
        checkAtomicIndex(index);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        synchronized (arrayBuffer) {
            short old = arrayBuffer.buffer.getShort(addr);
            // Do math as integers because we need to handle overflow
            int r = f.apply((int) old, (int) val);
            arrayBuffer.buffer.putShort(addr, (short) (r & 0xffff));
            return Conversions.shortBitsToUint(old);
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
        short val = Conversions.toUint16(v);
        checkAtomicIndex(index);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        synchronized (arrayBuffer) {
            short old = arrayBuffer.buffer.getShort(addr);
            arrayBuffer.buffer.putShort(addr, val);
            return Conversions.shortBitsToUint(old);
        }
    }

    @Override
    public Object atomicCompareAndExchange(int index, Object e, Object r) {
        short expected = Conversions.toUint16(e);
        short replacement = Conversions.toUint16(r);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            short old = arrayBuffer.buffer.getShort(addr);
            if (old == expected) {
                arrayBuffer.buffer.putShort(addr, replacement);
            }
            return Conversions.shortBitsToUint(old);
        }
    }
}
