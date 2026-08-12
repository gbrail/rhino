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
 * An array view that stores 32-bit quantities and implements the JavaScript "Int32Array" interface.
 * It also implements List&lt;Integer&gt; for direct manipulation in Java.
 */
public class NativeInt32Array extends NativeTypedArrayView<Integer> {
    @Serial private static final long serialVersionUID = 2090724894289667699L;

    private static final String CLASS_NAME = "Int32Array";
    private static final int BYTES_PER_ELEMENT = 4;

    private static final ClassDescriptor DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(
                                CLASS_NAME,
                                3,
                                NativeTypedArrayView::typeError,
                                NativeInt32Array::js_constructor)
                        .withProp(CTOR, "BYTES_PER_ELEMENT", value(4))
                        .withProp(PROTO, "BYTES_PER_ELEMENT", value(4))
                        .withProp(CTOR, SymbolKey.SPECIES, ScriptRuntimeES6::symbolSpecies)
                        .build();
    }

    public NativeInt32Array() {
        super(Integer.TYPE);
    }

    public NativeInt32Array(NativeArrayBuffer ab, int off, int len) {
        super(Integer.TYPE, ab, off, len, len * BYTES_PER_ELEMENT);
    }

    public NativeInt32Array(int len) {
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
                NativeInt32Array::new,
                4,
                TopLevel.Builtins.Int32Array);
    }

    @Override
    protected Object js_get(int index) {
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        // Must explicitly coerce for performance
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
    public Integer get(int i) {
        ensureIndex(i);
        return (Integer) js_get(i);
    }

    @Override
    public Integer set(int i, Integer aByte) {
        ensureIndex(i);
        return (Integer) js_set(i, aByte);
    }

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
}
