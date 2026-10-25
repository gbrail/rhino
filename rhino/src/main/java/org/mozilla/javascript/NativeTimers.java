/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Implements the "setTimeout", "clearTimeout", and "queueMicrotask" functions of the HTML standard,
 * backed by the {@link TaskQueue} of the context on which they are called.
 *
 * <p>This is not installed by {@link Context#initStandardObjects()}; hosts that want timers must
 * call {@link #init(VarScope)} on the global scope. Tasks only run when the host pumps the
 * context's task queue, e.g. with {@link TaskQueue#runTasks()} or {@link TaskQueue#runAllTasks()}.
 */
public class NativeTimers {
    private int lastId = 0;
    private final HashMap<Integer, TaskQueue.Task> timers = new HashMap<>();

    private NativeTimers() {}

    /**
     * Install the "setTimeout", "clearTimeout", and "queueMicrotask" functions on the specified
     * scope.
     *
     * @param scope the scope (usually the global scope) where the functions should be defined
     */
    public static void init(VarScope scope) {
        NativeTimers self = new NativeTimers();
        var setTimeout = new LambdaFunction(scope, "setTimeout", 1, self::setTimeout);
        ScriptableObject.defineProperty(scope, "setTimeout", setTimeout, ScriptableObject.DONTENUM);
        var clearTimeout = new LambdaFunction(scope, "clearTimeout", 1, self::clearTimeout);
        ScriptableObject.defineProperty(
                scope, "clearTimeout", clearTimeout, ScriptableObject.DONTENUM);
        var queueMicrotask = new LambdaFunction(scope, "queueMicrotask", 1, self::queueMicrotask);
        ScriptableObject.defineProperty(
                scope, "queueMicrotask", queueMicrotask, ScriptableObject.DONTENUM);
    }

    private Object setTimeout(Context cx, VarScope scope, Object to, Object[] args) {
        if (args.length == 0) {
            throw ScriptRuntime.typeErrorById(
                    "msg.method.missing.parameter", "setTimeout", 1, args.length);
        }
        if (!(args[0] instanceof Callable func)) {
            throw ScriptRuntime.typeErrorById("msg.function.expected");
        }
        int id = ++lastId;
        int delay = 0;
        if (args.length > 1) {
            delay = ScriptRuntime.toInt32(args[1]);
        }
        Object[] funcArgs;
        if (args.length > 2) {
            funcArgs = new Object[args.length - 2];
            System.arraycopy(args, 2, funcArgs, 0, funcArgs.length);
        } else {
            funcArgs = ScriptRuntime.emptyArgs;
        }
        timers.put(
                id,
                cx.getTaskQueue()
                        .schedule(
                                tcx -> {
                                    // Remove the entry when the timer fires so that a later
                                    // clearTimeout on the same id is a no-op and the map does not
                                    // hold on to the callback.
                                    timers.remove(id);
                                    Object thisObj =
                                            ScriptableObject.getTopLevelScope(scope)
                                                    .getGlobalThis();
                                    func.call(tcx, scope, thisObj, funcArgs);
                                },
                                delay,
                                TimeUnit.MILLISECONDS));
        return id;
    }

    private Object clearTimeout(Context cx, VarScope scope, Object to, Object[] args) {
        if (args.length == 0) {
            throw ScriptRuntime.typeErrorById(
                    "msg.method.missing.parameter", "clearTimeout", 1, args.length);
        }
        int id = ScriptRuntime.toInt32(args[0]);
        TaskQueue.Task timer = timers.remove(id);
        if (timer != null) {
            timer.cancel();
        }
        return Undefined.instance;
    }

    private Object queueMicrotask(Context cx, VarScope scope, Object to, Object[] args) {
        if (args.length == 0) {
            throw ScriptRuntime.typeErrorById(
                    "msg.method.missing.parameter", "queueMicrotask", 1, args.length);
        }
        if (!(args[0] instanceof Callable func)) {
            throw ScriptRuntime.typeErrorById("msg.function.expected");
        }
        cx.getTaskQueue()
                .enqueueMicrotask(
                        () ->
                                func.call(
                                        cx,
                                        scope,
                                        Undefined.SCRIPTABLE_UNDEFINED,
                                        ScriptRuntime.emptyArgs));
        return Undefined.instance;
    }
}
