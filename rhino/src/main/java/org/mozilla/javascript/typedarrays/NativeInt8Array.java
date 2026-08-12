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
 * An array view that stores 8-bit quantities and implements the JavaScript "Int8Array" interface.
 * It also implements List&lt;Byte&gt; for direct manipulation in Java.
 */
public class NativeInt8Array extends NativeTypedArrayView<Byte> implements AtomicSupport {
    @Serial private static final long serialVersionUID = 6655250857400433124L;

    private static final String CLASS_NAME = "Int8Array";

    private static final ClassDescriptor DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(
                                CLASS_NAME,
                                3,
                                NativeTypedArrayView::typeError,
                                NativeInt8Array::js_constructor)
                        .withProp(CTOR, "BYTES_PER_ELEMENT", value(1))
                        .withProp(PROTO, "BYTES_PER_ELEMENT", value(1))
                        .withProp(CTOR, SymbolKey.SPECIES, ScriptRuntimeES6::symbolSpecies)
                        .build();
    }

    public NativeInt8Array() {
        // Not going to actually use the accessor so this doesn't matter
        super(Integer.TYPE);
    }

    public NativeInt8Array(NativeArrayBuffer ab, int off, int len) {
        super(Integer.TYPE, ab, off, len, len);
    }

    public NativeInt8Array(int len) {
        this(new NativeArrayBuffer(len), 0, len);
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
        return 1;
    }

    private static Object js_constructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return NativeTypedArrayView.js_constructor(
                cx, f, nt, s, thisObj, args, NativeInt8Array::new, 1, TopLevel.Builtins.Int8Array);
    }

    @Override
    protected Object js_get(int index) {
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        return arrayBuffer.buffer.get(index + offset);
    }

    @Override
    protected Object js_set(int index, Object c) {
        int val = Conversions.toInt8(c);
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        arrayBuffer.buffer.put(index + offset, (byte) val);
        return null;
    }

    // List implementation (much of it handled by the superclass)

    @Override
    public Byte get(int i) {
        ensureIndex(i);
        return (Byte) js_get(i);
    }

    @Override
    public Byte set(int i, Byte aByte) {
        ensureIndex(i);
        return (Byte) js_set(i, aByte);
    }

    // VarHandle does not support any operations on bytes, so we have to do
    // everything with an explicit lock

    @Override
    public Object atomicLoad(int index) {
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            return arrayBuffer.buffer.get(index + offset);
        }
    }

    @Override
    public Object atomicStore(int index, Object v) {
        double num = coerceNumber(v);
        int val = ScriptRuntime.toInt32(num);
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            arrayBuffer.buffer.put(index + offset, (byte) val);
        }
        return num;
    }

    private Object mathOp(int index, Object v, BiFunction<Integer, Integer, Integer> f) {
        int val = Conversions.toInt8(v);
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            int addr = index + offset;
            int old = arrayBuffer.buffer.get(addr);
            int r = f.apply(old, val);
            arrayBuffer.buffer.put(addr, (byte) r);
            return (byte) old;
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
        int val = Conversions.toInt8(v);
        checkAtomicIndex(index);
        int addr = index + offset;
        synchronized (arrayBuffer) {
            int old = arrayBuffer.buffer.get(addr);
            arrayBuffer.buffer.put(addr, (byte) val);
            return (byte) old;
        }
    }

    @Override
    public Object atomicCompareAndExchange(int index, Object e, Object r) {
        int expected = Conversions.toInt8(e);
        int replacement = Conversions.toInt8(r);
        int addr = index + offset;
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            int old = arrayBuffer.buffer.get(addr);
            if (old == expected) {
                arrayBuffer.buffer.put(addr, (byte) replacement);
            }
            return (byte) old;
        }
    }
}
