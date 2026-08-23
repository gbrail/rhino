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
 * An array view that stores 32-bit quantities and implements the JavaScript "Int32Array" interface.
 * It also implements List&lt;Integer&gt; for direct manipulation in Java.
 */
public class NativeInt32Array extends NativeTypedArrayView<Integer>
        implements AtomicSupport, WaitSupport {
    @Serial private static final long serialVersionUID = 2090724894289667699L;

    private static final String CLASS_NAME = "Int32Array";
    protected static final int BYTES_PER_ELEMENT = 4;

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
                shared ? NativeSharedInt32Array::new : NativeInt32Array::new,
                4,
                TopLevel.Builtins.Int32Array);
    }

    @Override
    protected Object js_get(int index) {
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        return arrayBuffer.buffer.getInt((index * BYTES_PER_ELEMENT) + offset);
    }

    @Override
    protected Object js_set(int index, Object c) {
        int val = ScriptRuntime.toInt32(c);
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        arrayBuffer.buffer.putInt((index * BYTES_PER_ELEMENT) + offset, val);
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
    public Object atomicLoad(int index) {
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            return arrayBuffer.buffer.getInt((index * BYTES_PER_ELEMENT) + offset);
        }
    }

    @Override
    public Object atomicStore(int index, Object v) {
        double num = coerceNumber(v);
        int val = ScriptRuntime.toInt32(num);
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            arrayBuffer.buffer.putInt((index * BYTES_PER_ELEMENT) + offset, val);
        }
        return num;
    }

    private Object mathOp(int index, Object v, BiFunction<Integer, Integer, Integer> f) {
        int val = Conversions.toInt32(v);
        checkAtomicIndex(index);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        synchronized (arrayBuffer) {
            int old = arrayBuffer.buffer.getInt(addr);
            int r = f.apply(old, val);
            arrayBuffer.buffer.putInt(addr, r);
            return old;
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
        int val = Conversions.toInt32(v);
        checkAtomicIndex(index);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        synchronized (arrayBuffer) {
            int old = arrayBuffer.buffer.getInt(addr);
            arrayBuffer.buffer.putInt(addr, val);
            return old;
        }
    }

    @Override
    public Object atomicCompareAndExchange(int index, Object e, Object r) {
        int expected = Conversions.toInt32(e);
        int replacement = Conversions.toInt32(r);
        int addr = (index * BYTES_PER_ELEMENT) + offset;
        checkAtomicIndex(index);
        synchronized (arrayBuffer) {
            int old = arrayBuffer.buffer.getInt(addr);
            if (old == expected) {
                arrayBuffer.buffer.putInt(addr, replacement);
            }
            return old;
        }
    }

    // Support for wait/notify

    private transient volatile Waiters waiters = null;

    @Override
    public boolean isShared() {
        return arrayBuffer.isShared();
    }

    @Override
    public boolean isDetached() {
        return arrayBuffer.isDetached();
    }

    @Override
    public Object wait(int index, Object v, Object t) {
        checkAtomicIndex(index);
        int val = ScriptRuntime.toInt32(v);
        int timeout = Waiters.getTimeout(t);
        var w = getWaiters();
        var r = w.waitSync(index, timeout, () -> readCurrent(index) == val);
        return Waiters.resultToString(r);
    }

    @Override
    public Object waitAsync(int index, Object val, Object timeout) {
        throw ScriptRuntime.typeError("Not implemented yet");
    }

    @Override
    public Object notify(int index, int count) {
        checkAtomicIndex(index);
        return getWaiters().notify(index, count);
    }

    /** Reads the current value at the given index. Overridden for shared buffers. */
    protected int readCurrent(int index) {
        synchronized (arrayBuffer) {
            return arrayBuffer.buffer.getInt((index * BYTES_PER_ELEMENT) + offset);
        }
    }

    // Use double-checked locking, correct because waiters is volatile
    private Waiters getWaiters() {
        var w = waiters;
        if (w == null) {
            synchronized (this) {
                w = waiters;
                if (w == null) {
                    w = new Waiters();
                    waiters = w;
                }
            }
        }
        return w;
    }
}
