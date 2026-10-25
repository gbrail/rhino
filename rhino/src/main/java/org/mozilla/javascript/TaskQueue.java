/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayDeque;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The task queue associated with a {@link Context}. There is one task queue for each context,
 * created when the context is created and accessed via {@link Context#getTaskQueue()}.
 *
 * <p>This class owns the context's microtask queue, the unhandled promise rejection tracker, and
 * the FinalizationRegistry reference queue. It also supports timed tasks: tasks that are run a
 * given number of milliseconds after they are scheduled.
 *
 * <p>Tasks run on the thread associated with the context, and the queue's local state is not
 * thread-safe. The "schedule" methods and the pump methods ("processPending", "timeToNextTask",
 * "awaitNextTask") must be called from the context's thread. The "post" methods are safe to call
 * from any thread: a post from the context's thread goes directly to the local queue, while a post
 * from another thread is placed in a thread-safe inbox that the context's thread migrates when it
 * next pumps the queue. A post from another thread wakes a thread blocked in "awaitNextTask".
 */
public class TaskQueue {
    private final Context context;
    private final ArrayDeque<Runnable> microtasks = new ArrayDeque<>();
    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
    private int microtaskSuspendCount;
    private final UnhandledRejectionTracker unhandledPromises = new UnhandledRejectionTracker();
    private boolean finalizationEnabled = false;

    private final PriorityQueue<Scheduled> scheduled = new PriorityQueue<>();
    private long sequence = 0;
    private LongSupplier clock = System::currentTimeMillis;

    private final ConcurrentLinkedQueue<InboxTask> inbox = new ConcurrentLinkedQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition signalled = lock.newCondition();

    TaskQueue(Context context) {
        this.context = context;
    }

    /**
     * Add a task that will be executed at the end of the current operation. The various "evaluate"
     * functions will all call this before exiting to ensure that all microtasks run to completion.
     * Otherwise, callers should call "processMicrotasks" to run them all. This feature is primarily
     * used to implement Promises. The microtask queue is not thread-safe.
     */
    public void enqueueMicrotask(Runnable task) {
        microtasks.add(task);
    }

    /**
     * Run all the microtasks for the current context to completion. This is called by the various
     * "evaluate" functions. Frameworks that call Function objects directly should call this
     * function to ensure that everything completes if they want all Promises to eventually resolve.
     * This function is idempotent, but the microtask queue is not thread-safe. If references are
     * registered using a FinalizationRegistry, finalization callbacks will be called in this method
     * as well.
     *
     * <p>Nothing will happen if suspendMicrotaskProcessing was called.
     *
     * @see #suspendMicrotaskProcessing()
     */
    public void processMicrotasks() {
        if (microtaskSuspendCount > 0) {
            return;
        }
        // Clean up references in a microtask in case a finalization call
        // registers a microtask
        microtasks.add(this::cleanUpReferences);
        Runnable head;
        while ((head = microtasks.poll()) != null) {
            head.run();
        }
    }

    /**
     * Clean up an item that has been removed from the reference queue and check if there are more.
     */
    void cleanUpReferences() {
        Reference<?> ref;
        while ((ref = referenceQueue.poll()) != null) {
            if (ref instanceof NativeFinalizationRegistry.Registration registration) {
                var registry = registration.getRegistry();
                if (registry != null) {
                    registry.cleanup(context, registration);
                }
            }
        }
    }

    /**
     * Temporarily suspend microtask processing. Tasks will be resumed again when
     * resumeMicrotaskProcessing() is called. Suspensions are cumulative -- each call to this method
     * increases the suspend count, and microtasks will resume when the count again reaches zero.
     * This can be used in complex frameworks that wish to execute multiple individual scripts
     * before promises are resolved to emulate the behavior of some web browsers and other
     * frameworks. The suspend count is not thread safe.
     *
     * @see #resumeMicrotaskProcessing()
     */
    public void suspendMicrotaskProcessing() {
        microtaskSuspendCount++;
    }

    /**
     * Resume microtask processing after a suspend. Each call to suspendMicrotaskProcessing() must
     * be matched with a call to this function in order for processing to resume.
     *
     * @see #suspendMicrotaskProcessing()
     */
    public void resumeMicrotaskProcessing() {
        assert microtaskSuspendCount > 0;
        microtaskSuspendCount--;
        if (microtaskSuspendCount == 0) {
            processMicrotasks();
        }
    }

    /**
     * Replace the clock used to compute the deadlines of scheduled tasks. The default is {@link
     * System#currentTimeMillis()}. Tests may install a virtual clock so that tasks can be made due
     * deterministically. Deadlines are absolute values in the clock's time base, so the clock
     * should be installed before scheduling tasks.
     */
    public void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Schedule a task to run after "delayMillis" milliseconds. If the delay is zero or negative,
     * the task is immediately eligible to run. Tasks become eligible in order of their deadline,
     * with ties broken in the order they were scheduled. The task is invoked on the context's
     * thread, which has the context set on it at that time, and the context is passed to the task.
     *
     * <p>Must be called from the context's thread; to post a task from another thread, use {@link
     * #post(Consumer, long)} instead.
     *
     * @return a handle that can be used to cancel the task before it runs
     */
    public Task schedule(Consumer<Context> task, long delayMillis) {
        // Prevent a wraparound past Long.MAX_VALUE.
        long deadline = Math.max(clock.getAsLong() + Math.max(0, delayMillis), 0);
        var s =
                new Scheduled(task, deadline, ++sequence, new AtomicReference<>(State.PENDING));
        scheduled.add(s);
        return new Task(s);
    }

    /** Schedule a task that is immediately eligible to run. */
    public Task schedule(Consumer<Context> task) {
        return schedule(task, 0);
    }

    /**
     * Post a task to this queue, from any thread. If called from the context's thread, the task
     * goes directly to the local queue; otherwise it is placed in the inbox and migrated when the
     * context's thread next pumps the queue. A post from another thread wakes a thread blocked in
     * {@link #awaitNextTask(long)}. The task is invoked on the context's thread, which has the
     * context set on it at that time, and the context is passed to the task.
     *
     * @return a handle that can be used to cancel the task before it runs
     */
    public Task post(Consumer<Context> task, long delayMillis) {
        if (Context.getCurrentContext() == context) {
            return schedule(task, delayMillis);
        }
        long deadline = clock.getAsLong() + Math.max(0, delayMillis);
        InboxTask in = new InboxTask(task, deadline);
        inbox.add(in);
        lock.lock();
        try {
            signalled.signal();
        } finally {
            lock.unlock();
        }
        return new Task(in);
    }

    /** Post a task that is immediately eligible to run, from any thread. */
    public Task post(Consumer<Context> task) {
        return post(task, 0);
    }

    /**
     * Run all tasks that are due now, in deadline order, migrating any tasks posted from other
     * threads first. After each task, the microtask queue is drained, as the microtask checkpoint
     * requires. Must be called from the context's thread.
     *
     * @return true if at least one task was run
     */
    public boolean processPending() {
        boolean ran = false;
        for (; ; ) {
            Scheduled next = nextDue();
            if (next == null) {
                return ran;
            }
            next.task.accept(Context.getContext());
            processMicrotasks();
            ran = true;
        }
    }

    /**
     * Return the number of milliseconds until the next scheduled task is due, or 0 if a task is due
     * now. Migrates any tasks posted from other threads first. Must be called from the context's
     * thread.
     *
     * @return Long.MAX_VALUE if there are no scheduled tasks
     */
    public long timeToNextTask() {
        migrateInbox();
        for (; ; ) {
            Scheduled head = scheduled.peek();
            if (head == null) {
                return Long.MAX_VALUE;
            }
            if (!head.isPending()) {
                scheduled.poll();
                continue;
            }
            return Math.max(0, head.deadline - clock.getAsLong());
        }
    }

    /**
     * Block until the next scheduled task is due or "timeoutMillis" have elapsed, whichever happens
     * first, and run one task if one is then due, draining the microtask queue after it. The total
     * time this method may block is bounded by "timeoutMillis"; pass {@link Long#MAX_VALUE} to wait
     * indefinitely for the next task. A task posted from another thread wakes this method. Must be
     * called from the context's thread.
     *
     * @return true if a task was run, false if the total timeout expired with no task due
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public boolean awaitNextTask(long timeoutMillis) throws InterruptedException {
        // A real-time bound on the total wait. The (overridable) clock is only used for task
        // deadlines, so the timeout must be measured against wall time.
        final long limitNanos;
        if (timeoutMillis >= Long.MAX_VALUE / 1000000) {
            limitNanos = Long.MAX_VALUE;
        } else {
            limitNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        }
        while (true) {
            Scheduled next = null;
            lock.lock();
            try {
                next = nextDue();
                if (next == null) {
                    long wait = timeToNextTaskLocked();
                    long remainingNanos =
                            limitNanos == Long.MAX_VALUE
                                    ? Long.MAX_VALUE
                                    : limitNanos - System.nanoTime();
                    if (remainingNanos <= 0) {
                        return false;
                    }
                    long sleepNanos =
                            wait == Long.MAX_VALUE
                                    ? remainingNanos
                                    : Math.min(TimeUnit.MILLISECONDS.toNanos(wait), remainingNanos);
                    signalled.awaitNanos(sleepNanos);
                }
            } finally {
                lock.unlock();
            }
            if (next != null) {
                next.task.accept(Context.getContext());
                processMicrotasks();
                return true;
            }
            // Woken by a signal, a timeout, or spuriously: loop and re-check
        }
    }

    /**
     * Run tasks until the queue is empty, blocking as needed. This implements the host policy "stay
     * alive while work is pending"; it is what the shell and test harnesses use after a script
     * finishes. A task that schedules more work keeps the queue non-empty, so a self-scheduling
     * program (for example, a script that calls {@code setTimeout(() => setTimeout(f, 1), 1)}) runs
     * forever by design, the same way a pending timer keeps a Node.js process or a browser page
     * alive. If a pending task is cancelled from another thread, this method may notice up to the
     * cancelled task's remaining delay later, but it never blocks indefinitely once the queue is
     * empty. Hosts that want a different termination policy (an idle timeout, a wall-clock cap,
     * ...) should pump with the individual methods instead.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void runUntilEmpty() throws InterruptedException {
        while (true) {
            long wait = timeToNextTask();
            if (wait == Long.MAX_VALUE) {
                return;
            }
            awaitNextTask(wait);
        }
    }

    /**
     * Like {@link #timeToNextTask()}, but without migrating the inbox and without skipping
     * completed or cancelled tasks. The caller holds the lock, and this is only called from {@link
     * #awaitNextTask(long)} immediately after {@link #nextDue()} has returned null, which
     * guarantees that the head of the queue, if any, is still pending.
     */
    private long timeToNextTaskLocked() {
        Scheduled head = scheduled.peek();
        if (head == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, head.deadline - clock.getAsLong());
    }

    /**
     * Move any tasks posted from other threads into the local queue. Only the context's thread may
     * call this, but the inbox itself is thread-safe, so no lock is needed.
     */
    private void migrateInbox() {
        InboxTask in;
        while ((in = inbox.poll()) != null) {
            if (in.state.get() == State.PENDING) {
                scheduled.add(new Scheduled(in.task, in.deadline, ++sequence, in.state));
            }
        }
    }

    /**
     * Return and remove the next task that is due now, or null if none is due. Migrates any tasks
     * posted from other threads first. A task that is dequeued is marked as running; if a cancel
     * arrives in between, the task is dropped and never run.
     */
    private Scheduled nextDue() {
        migrateInbox();
        for (; ; ) {
            Scheduled head = scheduled.peek();
            if (head == null) {
                return null;
            }
            if (!head.isPending()) {
                scheduled.poll();
                continue;
            }
            if (head.deadline <= clock.getAsLong()) {
                scheduled.poll();
                if (head.beginRun()) {
                    return head;
                }
                continue;
            }
            return null;
        }
    }

    ReferenceQueue<Object> getReferenceQueue() {
        return referenceQueue;
    }

    /**
     * Control whether to track unhandled promise rejections. If "track" is set to true, then the
     * tracker returned by "getUnhandledPromiseTracker" must be periodically used to process the
     * queue of unhandled promise rejections, or a memory leak may result.
     *
     * @param track if true, then track unhandled promise rejections
     */
    public void setTrackUnhandledPromiseRejections(boolean track) {
        unhandledPromises.enable(track);
    }

    /**
     * Return the object used to track unhandled promise rejections.
     *
     * @return the tracker object
     */
    public UnhandledRejectionTracker getUnhandledPromiseTracker() {
        return unhandledPromises;
    }

    /**
     * Control whether finalization callbacks are dispatched when registered targets are garbage
     * collected. Defaults to false. If set to true, then "processMicrotasks" must periodically be
     * called to ensure that finalization callbacks are fired, or a memory leak may result.
     * (Microtasks are always processed before scripts exit and as a normal part of handling
     * Promises.) It set to false (the default) then the FinalizationRegistry API will still work as
     * the spec describes but callbacks will never fire.
     */
    public void setFinalizationEnabled(boolean enabled) {
        finalizationEnabled = enabled;
    }

    /** Returns whether cleanup callbacks are dispatched at microtask checkpoints. */
    public boolean isFinalizationEnabled() {
        return finalizationEnabled;
    }

    /** A handle to a scheduled task, as returned by the "schedule" and "post" methods. */
    public static final class Task {
        private final Cancelable cancelable;

        private Task(Cancelable cancelable) {
            this.cancelable = cancelable;
        }

        /**
         * Cancel the task if it has not yet run. If this method returns true, the task will never
         * run.
         *
         * @return true if the task was cancelled, false if it had already run or been cancelled
         */
        public boolean cancel() {
            return cancelable.cancel();
        }
    }

    /** A task that can be cancelled. */
    private interface Cancelable {
        boolean cancel();
    }

    /** The lifecycle state of a task. */
    private enum State {
        PENDING,
        CANCELLED,
        RUNNING
    }

    /** A scheduled task, kept in deadline order. */
    private static final class Scheduled implements Comparable<Scheduled>, Cancelable {
        final Consumer<Context> task;
        final long deadline;
        final long seq;
        final AtomicReference<State> state;

        Scheduled(Consumer<Context> task, long deadline, long seq, AtomicReference<State> state) {
            this.task = task;
            this.deadline = deadline;
            this.seq = seq;
            this.state = state;
        }

        @Override
        public int compareTo(Scheduled o) {
            int c = Long.compare(deadline, o.deadline);
            return c != 0 ? c : Long.compare(seq, o.seq);
        }

        @Override
        public boolean cancel() {
            return state.compareAndSet(State.PENDING, State.CANCELLED);
        }

        /** Mark this task as running. Returns false if it was cancelled in the meantime. */
        boolean beginRun() {
            return state.compareAndSet(State.PENDING, State.RUNNING);
        }

        boolean isPending() {
            return state.get() == State.PENDING;
        }
    }

    /** A task posted from another thread, waiting to be migrated to the local queue. */
    private static final class InboxTask implements Cancelable {
        final Consumer<Context> task;
        final long deadline;
        final AtomicReference<State> state = new AtomicReference<>(State.PENDING);

        InboxTask(Consumer<Context> task, long deadline) {
            this.task = task;
            this.deadline = deadline;
        }

        @Override
        public boolean cancel() {
            return state.compareAndSet(State.PENDING, State.CANCELLED);
        }
    }
}
