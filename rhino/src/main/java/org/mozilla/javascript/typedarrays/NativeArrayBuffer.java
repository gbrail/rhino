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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.mozilla.javascript.AbstractEcmaObjectOperations;
import org.mozilla.javascript.ClassDescriptor;
import org.mozilla.javascript.Constructable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.JSFunction;
import org.mozilla.javascript.LambdaConstructor;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.ScriptRuntimeES6;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.SymbolKey;
import org.mozilla.javascript.TopLevel;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.VarScope;

/**
 * A NativeArrayBuffer is the backing buffer for a typed array. Used inside JavaScript code, it
 * implements the ArrayBuffer interface. Used directly from Java, it simply holds a byte array.
 * NativeArrayBuffer is the implementation class for both ArrayBuffer and SharedArrayBuffer. Both
 * classes have the same internal methods but different constructors and prototypes.
 */
public class NativeArrayBuffer extends ScriptableObject {
    @Serial private static final long serialVersionUID = 3110411773054879549L;

    public static final String CLASS_NAME = "ArrayBuffer";

    private static final ClassDescriptor DESCRIPTOR;
    private static final ClassDescriptor SHARED_DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder("ArrayBuffer", 1, NativeArrayBuffer::js_constructor)
                        .withMethod(CTOR, "isView", 1, NativeArrayBuffer::js_isView)
                        .withMethod(PROTO, "slice", 2, NativeArrayBuffer::js_slice)
                        .withMethod(PROTO, "transfer", 0, NativeArrayBuffer::js_transfer)
                        .withMethod(PROTO, "resize", 1, NativeArrayBuffer::js_resize)
                        .withMethod(
                                PROTO,
                                "transferToFixedLength",
                                0,
                                NativeArrayBuffer::js_transferToFixedLength)
                        .withProp(
                                PROTO,
                                "byteLength",
                                NativeArrayBuffer::js_byteLength,
                                null,
                                DONTENUM)
                        .withProp(PROTO, "detached", NativeArrayBuffer::js_detached, null, DONTENUM)
                        .withProp(
                                PROTO, "resizable", NativeArrayBuffer::js_resizable, null, DONTENUM)
                        .withProp(
                                PROTO,
                                "maxByteLength",
                                NativeArrayBuffer::js_maxByteLength,
                                null,
                                DONTENUM)
                        .withProp(
                                PROTO,
                                SymbolKey.TO_STRING_TAG,
                                value("ArrayBuffer", DONTENUM | READONLY))
                        .withProp(CTOR, SymbolKey.SPECIES, ScriptRuntimeES6::symbolSpecies)
                        .build();

        SHARED_DESCRIPTOR =
                new ClassDescriptor.Builder(
                                "SharedArrayBuffer", 1, NativeArrayBuffer::js_sharedConstructor)
                        .withMethod(CTOR, "isView", 1, NativeArrayBuffer::js_isView)
                        .withMethod(PROTO, "slice", 2, NativeArrayBuffer::js_sharedSlice)
                        .withMethod(PROTO, "grow", 1, NativeArrayBuffer::js_sharedGrow)
                        .withMethod(PROTO, "transfer", 0, NativeArrayBuffer::js_throwNotBuffer)
                        .withMethod(PROTO, "resize", 1, NativeArrayBuffer::js_throwNotBuffer)
                        .withProp(
                                PROTO,
                                "byteLength",
                                NativeArrayBuffer::js_sharedByteLength,
                                null,
                                DONTENUM)
                        .withProp(
                                PROTO,
                                "maxByteLength",
                                NativeArrayBuffer::js_sharedMaxByteLength,
                                null,
                                DONTENUM)
                        .withProp(
                                PROTO,
                                "growable",
                                NativeArrayBuffer::js_sharedGrowable,
                                null,
                                DONTENUM)
                        .withProp(
                                PROTO,
                                SymbolKey.TO_STRING_TAG,
                                value("SharedArrayBuffer", DONTENUM | READONLY))
                        .withProp(CTOR, SymbolKey.SPECIES, ScriptRuntimeES6::symbolSpecies)
                        .build();
    }

    ByteBuffer buffer;
    // ES2024: maxByteLength for resizable buffers (-1 = fixed-length)
    private int maxByteLength = -1;
    // The original byte order, which we need to flip around in DataView a lot
    protected final ByteOrder byteOrder;
    private final boolean shared;

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    public static Object init(Context cx, VarScope scope, boolean sealed) {
        return DESCRIPTOR.buildConstructor(cx, scope, new NativeObject(), sealed);
    }

    public static Object initShared(Context cx, VarScope scope, boolean sealed) {
        return SHARED_DESCRIPTOR.buildConstructor(cx, scope, new NativeObject(), sealed);
    }

    /** Create an empty buffer. */
    public NativeArrayBuffer() {
        this(false);
    }

    public NativeArrayBuffer(boolean shared) {
        byteOrder = defaultByteOrder();
        buffer = allocateBuffer(0, shared);
        this.shared = shared;
    }

    /** Create a buffer of the specified length in bytes. */
    public NativeArrayBuffer(double len, boolean shared) {
        this(ScriptRuntime.toIndex(len), shared);
    }

    public NativeArrayBuffer(double len) {
        this(ScriptRuntime.toIndex(len), false);
    }

    private NativeArrayBuffer(int len, boolean shared) {
        byteOrder = defaultByteOrder();
        this.shared = shared;
        try {
            buffer = allocateBuffer(len, shared);
        } catch (OutOfMemoryError e) {
            throw ScriptRuntime.rangeErrorById("msg.arraybuf.oom");
        }
    }

    /** Get the number of bytes in the buffer. */
    public int getLength() {
        return buffer != null ? buffer.limit() : 0;
    }

    /**
     * Return the actual bytes that back the buffer. This is a reference to the real buffer, so
     * changes to bytes here will be reflected in the actual object and all its views.
     *
     * @deprecated this method will return null for shared array buffers. Callers should use {@link
     *     #buffer} to get the underlying ByteBuffer instead.
     */
    @Deprecated
    @SuppressWarnings("ByteBufferBackingArray")
    public byte[] getBuffer() {
        return buffer.hasArray() ? buffer.array() : null;
    }

    public ByteBuffer buffer() {
        return buffer;
    }

    public void detach() {
        buffer = null;
    }

    public boolean isDetached() {
        return buffer == null;
    }

    public boolean isShared() {
        return shared;
    }

    /**
     * Return a new buffer that represents a slice of this buffer's content, starting at position
     * "start" and ending at position "end". Both values will be "clamped" as per the JavaScript
     * spec so that invalid values may be passed and will be adjusted up or down accordingly. This
     * method will return a new buffer that contains a copy of the original buffer. Changes there
     * will not affect the content of the buffer.
     *
     * @param s the position where the new buffer will start
     * @param e the position where it will end
     */
    public NativeArrayBuffer slice(double s, double e) {
        // Handle negative start as relative to start
        // Clamp as per the spec to between 0 and length
        int end =
                ScriptRuntime.toInt32(
                        Math.max(0, Math.min(getLength(), (e < 0 ? getLength() + e : e))));
        int start =
                ScriptRuntime.toInt32(Math.min(end, Math.max(0, (s < 0 ? getLength() + s : s))));
        int len = end - start;

        NativeArrayBuffer newBuf = new NativeArrayBuffer(len, shared);
        buffer.position(start);
        newBuf.buffer.put(buffer);
        buffer.rewind();
        newBuf.buffer.flip();
        return newBuf;
    }

    private static NativeArrayBuffer getSelf(Object thisObj) {
        var self = LambdaConstructor.convertThisObject(thisObj, NativeArrayBuffer.class);
        if (self.shared) {
            throw ScriptRuntime.typeErrorById("msg.arraybuf.notarraybuf");
        }
        return self;
    }

    private static NativeArrayBuffer getSharedSelf(Object thisObj) {
        var self = LambdaConstructor.convertThisObject(thisObj, NativeArrayBuffer.class);
        if (!self.shared) {
            throw ScriptRuntime.typeErrorById("msg.arraybuf.notsharedarraybuf");
        }
        return self;
    }

    private static NativeArrayBuffer js_constructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double length = isArg(args, 0) ? ScriptRuntime.toIndex(args[0]) : 0;
        int maxByteLength = getMaxByteLength(args, length);
        NativeArrayBuffer buffer = new NativeArrayBuffer(length, false);
        buffer.maxByteLength = maxByteLength;
        ScriptRuntime.setBuiltinProtoAndParent(buffer, f, nt, s, TopLevel.Builtins.ArrayBuffer);
        return buffer;
    }

    private static NativeArrayBuffer js_sharedConstructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double length = isArg(args, 0) ? ScriptRuntime.toIndex(args[0]) : 0;
        int maxByteLength = getMaxByteLength(args, length);
        NativeArrayBuffer buffer = new NativeArrayBuffer(length, true);
        buffer.maxByteLength = maxByteLength;
        ScriptRuntime.setBuiltinProtoAndParent(
                buffer, f, nt, s, TopLevel.Builtins.SharedArrayBuffer);
        return buffer;
    }

    private static int getMaxByteLength(Object[] args, double length) {
        int maxLen = -1;
        if (isArg(args, 1) && args[1] instanceof Scriptable) {
            Scriptable options = (Scriptable) args[1];
            Object maxByteLengthValue = ScriptableObject.getProperty(options, "maxByteLength");
            if (maxByteLengthValue != Scriptable.NOT_FOUND
                    && !Undefined.isUndefined(maxByteLengthValue)) {
                maxLen = ScriptRuntime.toIndex(maxByteLengthValue);
                if (length > maxLen) {
                    throw ScriptRuntime.rangeErrorById("msg.arraybuf.range.mismatch");
                }
                if (maxLen > Runtime.getRuntime().maxMemory()) {
                    // Sanity check (in the 262 tests) to avoid an impossibly-large maximum
                    throw ScriptRuntime.rangeErrorById("msg.arraybuf.range.toobig");
                }
            }
        }
        return maxLen;
    }

    private static Boolean js_isView(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return isArg(args, 0) && (args[0] instanceof NativeArrayBufferView);
    }

    private static NativeArrayBuffer js_slice(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeArrayBuffer self = getSelf(thisObj);
        return self.sliceImpl(cx, s, args);
    }

    private static NativeArrayBuffer js_sharedSlice(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeArrayBuffer self = getSharedSelf(thisObj);
        return self.sliceImpl(cx, s, args);
    }

    private NativeArrayBuffer sliceImpl(Context cx, VarScope s, Object[] args) {
        checkDetached();
        double start = isArg(args, 0) ? ScriptRuntime.toNumber(args[0]) : 0;
        double end = isArg(args, 1) ? ScriptRuntime.toNumber(args[1]) : getLength();
        int endI =
                ScriptRuntime.toInt32(
                        Math.max(0, Math.min(getLength(), (end < 0 ? getLength() + end : end))));
        int startI =
                ScriptRuntime.toInt32(
                        Math.min(endI, Math.max(0, (start < 0 ? getLength() + start : start))));
        int len = endI - startI;

        var species = shared ? TopLevel.Builtins.SharedArrayBuffer : TopLevel.Builtins.ArrayBuffer;
        Constructable constructor =
                AbstractEcmaObjectOperations.speciesConstructor(
                        cx,
                        this,
                        TopLevel.getBuiltinCtor(cx, ScriptableObject.getTopLevelScope(s), species));
        Scriptable newBuf = constructor.construct(cx, s, new Object[] {len});
        if (!(newBuf instanceof NativeArrayBuffer)) {
            throw ScriptRuntime.typeErrorById("msg.species.invalid.ctor");
        }
        NativeArrayBuffer buf = (NativeArrayBuffer) newBuf;

        if (buf == this) {
            throw ScriptRuntime.typeErrorById("msg.arraybuf.same");
        }

        int actualLength = buf.getLength();
        if (actualLength < len) {
            throw ScriptRuntime.typeErrorById("msg.arraybuf.smaller.len", len, actualLength);
        }

        var tmp = buffer.duplicate();
        tmp.position(startI);
        tmp.limit(startI + len);
        buf.buffer.put(tmp);
        buf.buffer.rewind();
        return buf;
    }

    private static Object js_byteLength(Scriptable thisObj) {
        return getSelf(thisObj).getLength();
    }

    private static Object js_sharedByteLength(Scriptable thisObj) {
        return getSharedSelf(thisObj).getLength();
    }

    private static Object js_detached(Scriptable thisObj) {
        return getSelf(thisObj).isDetached();
    }

    private NativeArrayBuffer copyAndDetach(
            Context cx, VarScope scope, Object lenObj, boolean preserveResizability) {
        assert !shared;
        int newLength;
        if (Undefined.isUndefined(lenObj)) {
            newLength = getLength();
        } else {
            newLength = ScriptRuntime.toIndex(lenObj);
        }
        checkDetached();

        var arg2 = cx.newObject(scope);
        if (preserveResizability && maxByteLength >= 0) {
            arg2.put("maxByteLength", arg2, maxByteLength);
        }

        var constructor =
                AbstractEcmaObjectOperations.speciesConstructor(
                        cx,
                        this,
                        TopLevel.getBuiltinCtor(
                                cx,
                                ScriptableObject.getTopLevelScope(scope),
                                TopLevel.Builtins.ArrayBuffer));
        var newBuf = constructor.construct(cx, scope, new Object[] {newLength, arg2});
        if (!(newBuf instanceof NativeArrayBuffer)) {
            throw ScriptRuntime.typeErrorById("msg.species.invalid.ctor");
        }
        var newBuffer = (NativeArrayBuffer) newBuf;
        int copyLength = Math.min(newLength, getLength());
        if (copyLength > 0) {
            var tmp = buffer.duplicate();
            tmp.limit(copyLength);
            newBuffer.buffer.put(tmp);
            newBuffer.buffer.rewind();
        }
        detach();
        return newBuffer;
    }

    // ES2025 ArrayBuffer.prototype.transfer
    private static Scriptable js_transfer(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        var self = getSelf(thisObj);
        var arg1 = args.length > 0 ? args[0] : Undefined.instance;
        return self.copyAndDetach(cx, s, arg1, true);
    }

    // ES2025 ArrayBuffer.prototype.transferToFixedLength
    private static Scriptable js_transferToFixedLength(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        var self = getSelf(thisObj);
        var arg1 = args.length > 0 ? args[0] : Undefined.instance;
        return self.copyAndDetach(cx, s, arg1, false);
    }

    private static boolean isArg(Object[] args, int i) {
        return ((args.length > i) && !Undefined.instance.equals(args[i]));
    }

    private static Object js_resize(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeArrayBuffer self = getSelf(thisObj);
        if (!self.isResizable()) {
            throw ScriptRuntime.typeErrorById("msg.arraybuf.notresizeable");
        }
        var arg1 = args.length > 0 ? args[0] : Undefined.instance;
        int newLength = ScriptRuntime.toIndex(arg1);
        self.checkDetached();
        if (newLength > self.maxByteLength) {
            throw ScriptRuntime.rangeErrorById("msg.arraybuf.range.exceedsmax", self.maxByteLength);
        }
        int oldLength = self.getLength();
        if (newLength == oldLength) {
            // No resize needed
            return Undefined.instance;
        }

        var newBuffer = self.allocateBuffer(newLength, false);
        int copyLength = Math.min(newLength, oldLength);

        if (copyLength > 0) {
            var tmp = self.buffer.duplicate();
            tmp.limit(copyLength);
            newBuffer.put(tmp);
            newBuffer.rewind();
        }

        // New bytes are automatically initialized to 0 in Java
        self.buffer = newBuffer;
        return Undefined.instance;
    }

    private static Object js_sharedGrow(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeArrayBuffer self = getSharedSelf(thisObj);
        if (!self.isGrowable()) {
            throw ScriptRuntime.typeErrorById("msg.arraybuf.notresizeable");
        }
        var arg1 = args.length > 0 ? args[0] : Undefined.instance;
        int newLength = ScriptRuntime.toIndex(arg1);
        if (newLength > self.maxByteLength) {
            throw ScriptRuntime.rangeErrorById("msg.arraybuf.range.exceedsmax", self.maxByteLength);
        }
        int oldLength = self.getLength();
        if (newLength <= oldLength) {
            // No resize needed
            return Undefined.instance;
        }

        var newBuffer = self.allocateBuffer(newLength, true);
        int copyLength = Math.min(newLength, oldLength);

        if (copyLength > 0) {
            var tmp = self.buffer.duplicate();
            tmp.limit(copyLength);
            newBuffer.put(tmp);
            newBuffer.rewind();
        }

        // New bytes are automatically initialized to 0 in Java
        self.buffer = newBuffer;
        return Undefined.instance;
    }

    public boolean isResizable() {
        return maxByteLength >= 0;
    }

    public boolean isGrowable() {
        return maxByteLength >= 0;
    }

    private static Object js_resizable(Scriptable thisObj) {
        NativeArrayBuffer self = getSelf(thisObj);
        // A buffer is resizable if maxByteLength was specified in constructor
        return self.isResizable();
    }

    private static Object js_sharedGrowable(Scriptable thisObj) {
        NativeArrayBuffer self = getSharedSelf(thisObj);
        // A buffer is resizable if maxByteLength was specified in constructor
        return self.isGrowable();
    }

    private static Object js_maxByteLength(Scriptable thisObj) {
        NativeArrayBuffer self = getSelf(thisObj);
        // For fixed-length buffers, maxByteLength = byteLength
        // For resizable buffers, return the maxByteLength
        if (self.maxByteLength >= 0) {
            return self.maxByteLength;
        } else {
            return self.getLength();
        }
    }

    private static Object js_sharedMaxByteLength(Scriptable thisObj) {
        NativeArrayBuffer self = getSharedSelf(thisObj);
        // For fixed-length buffers, maxByteLength = byteLength
        // For resizable buffers, return the maxByteLength
        if (self.maxByteLength >= 0) {
            return self.maxByteLength;
        } else {
            return self.getLength();
        }
    }

    private static Object js_throwNotBuffer(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        throw ScriptRuntime.typeErrorById("msg.arraybuf.notarraybuf");
    }

    protected ByteBuffer allocateBuffer(int len, boolean shared) {
        var b = shared ? ByteBuffer.allocateDirect(len) : ByteBuffer.allocate(len);
        b.order(byteOrder);
        return b;
    }

    private static ByteOrder defaultByteOrder(Context cx) {
        return cx.hasFeature(Context.FEATURE_LITTLE_ENDIAN)
                ? ByteOrder.LITTLE_ENDIAN
                : ByteOrder.BIG_ENDIAN;
    }

    private static ByteOrder defaultByteOrder() {
        Context cx = Context.getCurrentContext();
        return cx == null ? ByteOrder.BIG_ENDIAN : defaultByteOrder(cx);
    }

    protected void checkDetached() {
        if (isDetached()) {
            throw ScriptRuntime.typeErrorById("msg.arraybuf.detached");
        }
    }
}
