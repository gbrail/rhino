package org.mozilla.javascript;

import static org.mozilla.javascript.ClassDescriptor.Builder.value;
import static org.mozilla.javascript.ClassDescriptor.Destination.CTOR;

import org.mozilla.javascript.typedarrays.AtomicSupport;
import org.mozilla.javascript.typedarrays.WaitSupport;

public class NativeAtomics extends ScriptableObject {
    private static final String ATOMICS_TAG = "Atomics";

    private static final ClassDescriptor DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(ATOMICS_TAG)
                        .withMethod(CTOR, "isLockFree", 1, NativeAtomics::isLockFree)
                        .withMethod(CTOR, "load", 2, NativeAtomics::load)
                        .withMethod(CTOR, "store", 3, NativeAtomics::store)
                        .withMethod(CTOR, "add", 3, NativeAtomics::add)
                        .withMethod(CTOR, "sub", 3, NativeAtomics::sub)
                        .withMethod(CTOR, "and", 3, NativeAtomics::and)
                        .withMethod(CTOR, "or", 3, NativeAtomics::or)
                        .withMethod(CTOR, "xor", 3, NativeAtomics::xor)
                        .withMethod(CTOR, "exchange", 3, NativeAtomics::exchange)
                        .withMethod(CTOR, "compareExchange", 4, NativeAtomics::compareAndExchange)
                        .withMethod(CTOR, "pause", 0, NativeAtomics::pause)
                        .withMethod(CTOR, "wait", 4, NativeAtomics::wait)
                        .withMethod(CTOR, "waitAsync", 4, NativeAtomics::waitAsync)
                        .withMethod(CTOR, "notify", 3, NativeAtomics::notify)
                        .withProp(
                                CTOR,
                                SymbolKey.TO_STRING_TAG,
                                value(ATOMICS_TAG, DONTENUM | READONLY))
                        .build();
    }

    static Object init(Context cx, VarScope s, boolean sealed) {
        return DESCRIPTOR.populateGlobal(cx, s, new NativeAtomics(), sealed);
    }

    private static int indexArg(Object[] args, int i) {
        return args.length > i ? ScriptRuntime.toIndex(args[i]) : 0;
    }

    private static Object objectArg(Object[] args, int i) {
        return args.length > i ? args[i] : Undefined.instance;
    }

    private static int countArg(Object[] args, int i) {
        double d;
        if (i >= args.length || Undefined.isUndefined(args[i])) {
            d = Double.POSITIVE_INFINITY;
        } else {
            d = ScriptRuntime.toIntegerOrInfinity(args[i]);
        }
        return Math.max(0, (int) d);
    }

    private NativeAtomics() {}

    @Override
    public String getClassName() {
        return ATOMICS_TAG;
    }

    private static AtomicSupport getAtomics(Object to) {
        if (to instanceof AtomicSupport s) {
            return s;
        }
        throw ScriptRuntime.typeErrorById("msg.atomics.not.supported.array");
    }

    private static WaitSupport getWaitable(Object to) {
        if (to instanceof WaitSupport ws) {
            return ws;
        }
        throw ScriptRuntime.typeErrorById("msg.atomics.not.supported.array");
    }

    private static Object isLockFree(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        var size = args.length > 0 ? ScriptRuntime.toIntegerOrInfinity(args[0]) : 0;
        // This needs to be kept in sync with the various implementations of NativeTypedArrayView.
        // Only int and long are fully atomic using VarHandle in Java.
        return size == 4 || size == 8;
    }

    private static Object load(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        Object t = objectArg(args, 0);
        var arr = getAtomics(t);
        int index = indexArg(args, 1);
        return arr.atomicLoad(index);
    }

    private static Object store(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        Object t = objectArg(args, 0);
        var arr = getAtomics(t);
        int index = indexArg(args, 1);
        Object val = objectArg(args, 2);
        return arr.atomicStore(index, val);
    }

    private static Object add(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        Object t = objectArg(args, 0);
        var arr = getAtomics(t);
        int index = indexArg(args, 1);
        Object val = objectArg(args, 2);
        return arr.atomicAdd(index, val);
    }

    private static Object sub(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        Object t = objectArg(args, 0);
        var arr = getAtomics(t);
        int index = indexArg(args, 1);
        Object val = objectArg(args, 2);
        return arr.atomicSub(index, val);
    }

    private static Object and(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        Object t = objectArg(args, 0);
        var arr = getAtomics(t);
        int index = indexArg(args, 1);
        Object val = objectArg(args, 2);
        return arr.atomicAnd(index, val);
    }

    private static Object or(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        Object t = objectArg(args, 0);
        var arr = getAtomics(t);
        int index = indexArg(args, 1);
        Object val = objectArg(args, 2);
        return arr.atomicOr(index, val);
    }

    private static Object xor(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        Object t = objectArg(args, 0);
        var arr = getAtomics(t);
        int index = indexArg(args, 1);
        Object val = objectArg(args, 2);
        return arr.atomicXor(index, val);
    }

    private static Object exchange(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        Object t = objectArg(args, 0);
        var arr = getAtomics(t);
        int index = indexArg(args, 1);
        Object val = objectArg(args, 2);
        return arr.atomicExchange(index, val);
    }

    private static Object compareAndExchange(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        Object t = objectArg(args, 0);
        var arr = getAtomics(t);
        int index = indexArg(args, 1);
        Object expected = objectArg(args, 2);
        Object replacement = objectArg(args, 3);
        return arr.atomicCompareAndExchange(index, expected, replacement);
    }

    @SuppressWarnings("ThreadPriorityCheck")
    private static Object pause(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        Thread.yield();
        return Undefined.instance;
    }

    private static Object wait(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        Object t = objectArg(args, 0);
        var arr = getWaitable(t);
        if (!arr.isShared()) {
            throw ScriptRuntime.typeErrorById("msg.arraybuf.notsharedarraybuf");
        }
        int index = indexArg(args, 1);
        // These have to be validated differently and in a very specific order
        Object val = objectArg(args, 2);
        Object timeout = objectArg(args, 3);
        return arr.wait(index, val, timeout);
    }

    private static Object waitAsync(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        Object t = objectArg(args, 0);
        var arr = getWaitable(t);
        if (!arr.isShared()) {
            throw ScriptRuntime.typeErrorById("msg.arraybuf.notsharedarraybuf");
        }
        int index = indexArg(args, 1);
        // These have to be validated differently and in a very specific order
        Object val = objectArg(args, 2);
        Object timeout = objectArg(args, 3);
        return arr.waitAsync(index, val, timeout);
    }

    private static Object notify(
            Context cx, JSFunction f, Object nt, VarScope s, Object to, Object[] args) {
        Object t = objectArg(args, 0);
        var arr = getWaitable(t);
        int index = indexArg(args, 1);
        int count = countArg(args, 2);
        return arr.notify(index, count);
    }
}
