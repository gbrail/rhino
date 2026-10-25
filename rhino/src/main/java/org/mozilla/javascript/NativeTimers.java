/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.HashMap;

/**
 * Implements the "setTimeout", "clearTimeout", and "queueMicrotask" functions of the HTML standard,
 * backed by the {@link TaskQueue} of the context on which they are called.
 *
 * <p>This is not installed by {@link Context#initStandardObjects()}; hosts that want timers must
 * call {@link #install(VarScope)} on the global scope. Tasks only run when the host pumps the
 * context's task queue, e.g. with {@link TaskQueue#processPending()} or {@link
 * TaskQueue#awaitNextTask(long)}.
 */
public class NativeTimers {
    private NativeTimers() {}

    /**
     * Install the "setTimeout", "clearTimeout", and "queueMicrotask" functions on the specified
     * scope.
     *
     * @param scope the scope (usually the global scope) where the functions should be defined
     */
    public static void install(VarScope scope) {
        TimerState state = new TimerState();
        LambdaFunction setTimeout =
                new LambdaFunction(
                        scope,
                        "setTimeout",
                        1,
                        (lcx, lscope, thisObj, args) -> setTimeout(lcx, lscope, state, args));
        ScriptableObject.defineProperty(scope, "setTimeout", setTimeout, ScriptableObject.DONTENUM);
        LambdaFunction clearTimeout =
                new LambdaFunction(
                        scope,
                        "clearTimeout",
                        1,
                        (lcx, lscope, thisObj, args) -> clearTimeout(state, args));
        ScriptableObject.defineProperty(
                scope, "clearTimeout", clearTimeout, ScriptableObject.DONTENUM);
        LambdaFunction queueMicrotask =
                new LambdaFunction(
                        scope,
                        "queueMicrotask",
                        1,
                        (lcx, lscope, thisObj, args) -> queueMicrotask(lcx, lscope, args));
        ScriptableObject.defineProperty(
                scope, "queueMicrotask", queueMicrotask, ScriptableObject.DONTENUM);
    }

    private static Object setTimeout(Context cx, VarScope scope, TimerState state, Object[] args) {
        if (args.length == 0) {
            throw ScriptRuntime.typeError("Expected function parameter");
        }
        if (!(args[0] instanceof Function)) {
            throw ScriptRuntime.typeError("Expected first argument to be a function");
        }
        int id = state.nextId();
        Function func = (Function) args[0];
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
        state.timers.put(
                id,
                cx.getTaskQueue()
                        .post(
                                timer -> {
                                    // Remove the entry when the timer fires so that a later
                                    // clearTimeout on the same id is a no-op and the map does not
                                    // hold on to the callback.
                                    state.timers.remove(id);
                                    runTimer(timer, scope, func, funcArgs);
                                },
                                delay));
        return id;
    }

    private static void runTimer(Context cx, VarScope scope, Function func, Object[] funcArgs) {
        Object thisObj = ScriptableObject.getTopLevelScope(scope).getGlobalThis();
        func.call(cx, scope, thisObj, funcArgs);
    }

    private static Object clearTimeout(TimerState state, Object[] args) {
        if (args.length == 0) {
            throw ScriptRuntime.typeError("Expected function parameter");
        }
        int id = ScriptRuntime.toInt32(args[0]);
        TaskQueue.Task timer = state.timers.remove(id);
        if (timer != null) {
            timer.cancel();
        }
        return Undefined.instance;
    }

    private static Object queueMicrotask(Context cx, VarScope scope, Object[] args) {
        if (args.length == 0) {
            throw ScriptRuntime.typeError("Expected function parameter");
        }
        if (!(args[0] instanceof Function)) {
            throw ScriptRuntime.typeError("Expected first argument to be a function");
        }
        Function func = (Function) args[0];
        cx.getTaskQueue()
                .enqueueMicrotask(
                        () ->
                                func.call(
                                        Context.getContext(),
                                        scope,
                                        Undefined.SCRIPTABLE_UNDEFINED,
                                        ScriptRuntime.emptyArgs));
        return Undefined.instance;
    }

    /** The per-install state: timer IDs and the tasks they refer to. */
    private static final class TimerState {
        private int lastId = 0;
        private final HashMap<Integer, TaskQueue.Task> timers = new HashMap<>();

        int nextId() {
            return ++lastId;
        }
    }
}
