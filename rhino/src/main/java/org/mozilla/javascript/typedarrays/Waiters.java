package org.mozilla.javascript.typedarrays;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import org.mozilla.javascript.ScriptRuntime;

/** This class implements the wait / notify capability of Atomics. */
class Waiters {
    enum Result {
        OK,
        NOT_EQUAL,
        TIMED_OUT
    };

    private static class Wtr {
        private final Thread thread;
        private boolean notified;

        Wtr(Thread thread) {
            this.thread = thread;
        }
    }

    private final ConcurrentHashMap<Integer, Queue<Wtr>> waiters = new ConcurrentHashMap<>();

    public Result waitSync(int index, int timeout, Supplier<Boolean> eq) {
        var r = new AtomicReference<>(Result.OK);
        var w = new Wtr(Thread.currentThread());

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

        // Park outside the critical section!
        LockSupport.parkNanos(timeout * 1000000L);

        if (w.notified) {
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
                            waiter.notified = true;
                            LockSupport.unpark(waiter.thread);
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
                return "ok";
            case NOT_EQUAL:
                return "not-equal";
            case TIMED_OUT:
                return "timed-out";
            default:
                throw new AssertionError();
        }
    }
}
