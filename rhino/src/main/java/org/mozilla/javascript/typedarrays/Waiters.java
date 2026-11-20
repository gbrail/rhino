package org.mozilla.javascript.typedarrays;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativePromise;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.TaskQueue;
import org.mozilla.javascript.VarScope;

/** This class implements the wait / notify capability of Atomics. */
class Waiters {
    private static final String OK = "ok";
    private static final String NOT_EQUAL = "not-equal";
    private static final String TIMED_OUT = "timed-out";

    enum Result {
        OK,
        NOT_EQUAL,
        TIMED_OUT
    };

    private static class Wtr {
        private final NativePromise promise;
        private final TaskQueue taskQueue;
        private final long expiration;

        private TaskQueue.Task timerTask;
        private boolean notified;
        private VarScope wakeScope;

        Wtr(int timeout) {
            this.promise = null;
            this.taskQueue = null;
            // TODO infinite timeout?
            this.expiration = System.currentTimeMillis() + timeout;
        }

        Wtr(Context cx, NativePromise p, int timeout) {
            this.promise = p;
            this.taskQueue = cx.getTaskQueue();
            this.expiration = System.currentTimeMillis() + timeout;
        }

        void wake(Result r) {
            setNotified();
            if (promise != null) {
                // We might be on a different thread -- we need to get to the right one
                final var origResult = r;
                taskQueue.post(
                        (lcx) -> {
                            var result = origResult;
                            // Check for timeout even on a normal notification, as per spec
                            if (System.currentTimeMillis() >= expiration) {
                                result = Result.TIMED_OUT;
                            }
                            // OK to cancel even if it may have timed out
                            timerTask.cancel();
                            promise.resolve(lcx, wakeScope, resultToString(result));
                        });
            }
        }

        synchronized void setNotified() {
            notified = true;
            notify();
        }

        synchronized boolean waitUntilNotified() {
            while (!notified) {
                long toWait = expiration - System.currentTimeMillis();
                if (toWait <= 0) {
                    return false;
                }
                try {
                    wait(toWait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    private final ConcurrentHashMap<Integer, Queue<Wtr>> waiters = new ConcurrentHashMap<>();

    public Result waitSync(int index, int timeout, Supplier<Boolean> eq) {
        var r = new AtomicReference<>(Result.OK);
        var w = new Wtr(timeout);

        waiters.compute(
                index,
                (i, l) -> {
                    // Critical section for "index".
                    if (!eq.get()) {
                        // Not equal -- bail out early
                        r.set(Result.NOT_EQUAL);
                        return l;
                    }
                    if (l == null) {
                        l = new ArrayDeque<>();
                    }
                    l.offer(w);
                    return l;
                });

        if (r.get() == Result.NOT_EQUAL) {
            return Result.NOT_EQUAL;
        }

        if (w.waitUntilNotified()) {
            return Result.OK;
        }

        // Clean up 'cause we timed out
        waiters.compute(
                index,
                (i, l) -> {
                    if (l != null) {
                        l.remove(w);
                        if (l.isEmpty()) {
                            return null;
                        }
                    }
                    return l;
                });
        return Result.TIMED_OUT;
    }

    public Object waitAsync(Context cx, VarScope s, int index, int timeout, Supplier<Boolean> eq) {
        var w = new AtomicReference<Wtr>();
        var r = new AtomicReference<>(Result.OK);

        waiters.compute(
                index,
                (i, l) -> {
                    // Critical section for "index".
                    if (!eq.get()) {
                        // Not equal -- bail out early
                        r.set(Result.NOT_EQUAL);
                        return l;
                    }
                    if (timeout == 0) {
                        // Also bail out early on zero timeout
                        r.set(Result.TIMED_OUT);
                        return l;
                    }
                    if (l == null) {
                        l = new ArrayDeque<>();
                    }

                    var p = NativePromise.create(s);
                    var wt = new Wtr(cx, p, timeout);
                    wt.wakeScope = s;
                    wt.timerTask =
                            cx.getTaskQueue()
                                    .schedule(
                                            (lcx) -> timedOut(index, wt),
                                            timeout,
                                            TimeUnit.MILLISECONDS);
                    l.offer(wt);
                    w.set(wt);

                    return l;
                });

        var result = cx.newObject(s);

        if (r.get() != Result.OK) {
            result.put("async", result, false);
            result.put("value", result, resultToString(r.get()));
        } else {
            var wt = w.get();
            assert wt != null;
            result.put("async", result, true);
            result.put("value", result, wt.promise);
        }

        return result;
    }

    private void timedOut(int index, Wtr w) {
        waiters.compute(
                index,
                (i, l) -> {
                    // Critical section for "index"
                    if (l != null) {
                        // Linear-time removal, maybe not great
                        if (l.remove(w)) {
                            w.wake(Result.TIMED_OUT);
                        }
                        if (l.isEmpty()) {
                            // Be sure to clean up when done
                            return null;
                        }
                    }
                    return l;
                });
    }

    public int notify(int index, int count) {
        var removed = new AtomicInteger(0);

        waiters.compute(
                index,
                (i, l) -> {
                    // Critical section for "index"
                    if (l != null) {
                        int toRemove = Math.min(count, l.size());
                        for (int c = 0; c < toRemove; c++) {
                            var waiter = l.remove();
                            waiter.wake(Result.OK);
                        }
                        removed.set(toRemove);
                        if (l.isEmpty()) {
                            // Be sure to clean up when done
                            return null;
                        }
                    }
                    return l;
                });

        return removed.get();
    }

    public int waiterCount(int index) {
        Queue<Wtr> l = waiters.get(index);
        return l == null ? 0 : l.size();
    }

    public static int getTimeout(Object t) {
        double d = ScriptRuntime.toNumber(t);
        if (Double.isNaN(d) || d == Double.POSITIVE_INFINITY) {
            return Integer.MAX_VALUE;
        }
        if (d == Double.NEGATIVE_INFINITY) {
            return 0;
        }
        return Math.max(0, (int) d);
    }

    public static String resultToString(Result r) {
        switch (r) {
            case OK:
                return OK;
            case NOT_EQUAL:
                return NOT_EQUAL;
            case TIMED_OUT:
                return TIMED_OUT;
            default:
                throw new AssertionError();
        }
    }
}
