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
 * An array view that stores 16-bit quantities and implements the JavaScript "Int16Array" interface.
 * It also implements List&lt;Short&gt; for direct manipulation in Java.
 */
public class NativeInt16Array extends NativeTypedArrayView<Short> {
    @Serial private static final long serialVersionUID = -8592870435287581398L;

    private static final String CLASS_NAME = "Int16Array";
    private static final int BYTES_PER_ELEMENT = 2;

    private static final ClassDescriptor DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(
                                CLASS_NAME,
                                3,
                                NativeTypedArrayView::typeError,
                                NativeInt16Array::js_constructor)
                        .withProp(CTOR, "BYTES_PER_ELEMENT", value(2))
                        .withProp(PROTO, "BYTES_PER_ELEMENT", value(2))
                        .withProp(CTOR, SymbolKey.SPECIES, ScriptRuntimeES6::symbolSpecies)
                        .build();
    }

    public NativeInt16Array() {
        super(Short.TYPE);
    }

    public NativeInt16Array(NativeArrayBuffer ab, int off, int len) {
        super(Short.TYPE, ab, off, len, len * BYTES_PER_ELEMENT);
    }

    public NativeInt16Array(int len) {
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
                NativeInt16Array::new,
                2,
                TopLevel.Builtins.Int16Array);
    }

    @Override
    protected Object js_get(int index) {
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        // Can't consolidate for performance
        short s = (short) accessor.get(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset);
        return s;
    }

    @Override
    protected Object js_set(int index, Object c) {
        short val = Conversions.toInt16(c);
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        accessor.set(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
        return null;
    }

    @Override
    public Short get(int i) {
        ensureIndex(i);
        return (Short) js_get(i);
    }

    @Override
    public Short set(int i, Short aByte) {
        ensureIndex(i);
        return (Short) js_set(i, aByte);
    }

    // In Java 17, only load and store can be atomic.
    // Others are going to require explicit locking.

    @Override
    public void checkAtomicSupport() {}

    @Override
    public Object atomicLoad(int index) {
        checkAtomicIndex(index);
        return accessor.getVolatile(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset);
    }

    @Override
    public Object atomicStore(int index, Object v) {
        double num = coerceNumber(v);
        short val = (short) ScriptRuntime.toInt32(num);
        checkAtomicIndex(index);
        accessor.setVolatile(arrayBuffer.buffer, (index * BYTES_PER_ELEMENT) + offset, val);
        return num;
    }

    private Object mathOp(int index, Object v, BiFunction<Short, Short, Short> f) {
        short val = Conversions.toInt16(v);
        checkAtomicIndex(index);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        synchronized (arrayBuffer) {
            short old = (short) accessor.get(arrayBuffer.buffer, addr);
            short r = f.apply(old, val);
            accessor.set(arrayBuffer.buffer, addr, r);
            return old;
        }
    }

    @Override
    public Object atomicAdd(int index, Object v) {
        return mathOp(index, v, (a, b) -> (short) (a + b));
    }

    @Override
    public Object atomicSub(int index, Object v) {
        return mathOp(index, v, (a, b) -> (short) (a - b));
    }

    @Override
    public Object atomicAnd(int index, Object v) {
        return mathOp(index, v, (a, b) -> (short) (a & b));
    }

    @Override
    public Object atomicOr(int index, Object v) {
        return mathOp(index, v, (a, b) -> (short) (a | b));
    }

    @Override
    public Object atomicXor(int index, Object v) {
        return mathOp(index, v, (a, b) -> (short) (a ^ b));
    }

    @Override
    public Object atomicExchange(int index, Object v) {
        short val = Conversions.toInt16(v);
        checkAtomicIndex(index);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        synchronized (arrayBuffer) {
            short old = (short) accessor.get(arrayBuffer.buffer, addr);
            accessor.set(arrayBuffer.buffer, addr, val);
            return old;
        }
    }

    @Override
    public Object atomicCompareAndExchange(int index, Object e, Object r) {
        short expected = Conversions.toInt16(e);
        short replacement = Conversions.toInt16(r);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            short old = (short) accessor.get(arrayBuffer.buffer, addr);
            if (old == expected) {
                accessor.set(arrayBuffer.buffer, addr, replacement);
            }
            return old;
        }
    }
}
