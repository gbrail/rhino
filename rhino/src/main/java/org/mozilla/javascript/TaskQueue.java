/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayDeque;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The task queue associated with a {@link Context}. There is one task queue for each context,
 * created when the context is created and accessed via {@link Context#getTaskQueue()}.
 *
 * <p>This class owns the context's microtask queue, the unhandled promise rejection tracker, and
 * the FinalizationRegistry reference queue. It also supports timed tasks: tasks that are run a
 * given time after they are scheduled.
 *
 * <p>The queue is not thread-safe and must be used from a single thread, the thread associated with
 * the context, which is also the thread on which tasks run. If an event on another thread must
 * result in a task running, it is the application's responsibility to wake the thread that pumps
 * the queue, for example by interrupting it, and to schedule the task once it runs.
 *
 * <p>Applications that use timers or need integration with an event loop must call {@link
 * #runTasks} to ensure that all tasks are executed. A simple application may simply call {@link
 * #runAllTasks}, which will run until all scheduled timers have executed, even if this takes
 * forever. A more sophisticated application may instead loop on {@link #runTasks} and block using
 * another mechanism in order to incorporate events from other event loops.
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

    TaskQueue(Context context) {
        this.context = context;
    }

    /**
     * Add a task that will be executed at the end of the current operation. The various "evaluate"
     * functions will all call this before exiting to ensure that all microtasks run to completion.
     * Otherwise, callers should call "processMicrotasks" to run them all.
     */
    public void enqueueMicrotask(Runnable task) {
        microtasks.add(task);
    }

    /**
     * Run all the microtasks for the current context to completion without blocking for any timers
     * that may be ready to fire. This is called by the various "evaluate" functions. Frameworks
     * that call Function objects directly should call this function to ensure that everything
     * completes if they want all Promises to eventually resolve. This function is idempotent, but
     * the microtask queue is not thread-safe. If references are registered using a
     * FinalizationRegistry, finalization callbacks will be called in this method as well.
     *
     * <p>This method does not run scheduled timers; call {@link #runTasks} for that.
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
     * System#currentTimeMillis()}. Tests that wish to override the clock must set this before
     * scheduling any timers. The clock is only used to compute task deadlines; {@link #runAllTasks}
     * always sleeps in real time, so it must not be used together with a custom clock.
     */
    public void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Schedule a task to run after "delay". If the delay is zero or negative, the task is
     * immediately eligible to run. A delay so large that its deadline overflows results in a task
     * that is never due. This method must be called from the thread that runs the queue.
     *
     * <p>Scheduled tasks will not be executed unless {@link #runTasks} or {@link #runAllTasks} is
     * called.
     *
     * @return a handle that can be used to cancel the task before it runs
     */
    public Task schedule(Consumer<Context> task, long delay, TimeUnit unit) {
        long delayMs;
        if (delay <= 0) {
            // A zero or negative delay is immediately eligible to run.
            delayMs = 0;
        } else {
            delayMs = unit.toMillis(delay);
        }
        long now = clock.getAsLong();
        long deadline;
        if (delayMs <= 0) {
            deadline = now;
        } else if (now > Long.MAX_VALUE - delayMs) {
            // The deadline would overflow; the task is never due.
            deadline = Long.MAX_VALUE;
        } else {
            deadline = now + delayMs;
        }
        var s = new Scheduled(task, deadline, ++sequence);
        scheduled.add(s);
        return new Task(s);
    }

    /**
     * Run all tasks that are due without blocking: timers whose deadline has arrived, microtasks,
     * and finalization callbacks. Timers run in order of their deadlines, with a microtask
     * checkpoint after each one, so a microtask scheduled by a timer runs before the next timer. A
     * timer that schedules another timer that is already due, for example with a zero delay, is
     * also run by this call.
     *
     * <p>If microtask processing is suspended, due timers are held in the microtask queue until
     * processing resumes.
     *
     * @return the number of milliseconds, according to the current clock, until the next scheduled
     *     task is due, or -1 if there are no scheduled tasks
     */
    public long runTasks() {
        while (true) {
            Scheduled next = nextDue();
            if (next != null) {
                next.beginRun();
                // Add to the microtask queue so that the task interleaves with existing work
                // and a microtask checkpoint runs after it.
                Consumer<Context> task = next.task;
                microtasks.add(() -> task.accept(context));
            }
            processMicrotasks();
            long nextDelay = timeToNextTask();
            if (nextDelay != 0) {
                return nextDelay;
            }
            // A task scheduled while running is already due: run it in the next iteration.
        }
    }

    /**
     * Run tasks until there are no scheduled tasks, blocking the current thread as needed. This is
     * the equivalent of looping on {@link #runTasks} and sleeping in between. It sleeps in real
     * time, so it must not be used together with a clock set by {@link #setClock}. A task that
     * schedules more work keeps the queue non-empty, so a self-scheduling program runs forever by
     * design.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void runAllTasks() throws InterruptedException {
        long nextDelay;
        while ((nextDelay = runTasks()) >= 0) {
            Thread.sleep(nextDelay);
        }
    }

    /** Return and remove the next task that is due now, or null if none is due. */
    private Scheduled nextDue() {
        Scheduled head;
        while ((head = scheduled.peek()) != null) {
            if (!head.isPending()) {
                // Cancelled: drop it.
                scheduled.poll();
                continue;
            }
            if (head.deadline <= clock.getAsLong()) {
                scheduled.poll();
                return head;
            }
            return null;
        }
        return null;
    }

    /**
     * Return the number of milliseconds until the next pending task is due, or -1 if there are no
     * pending tasks. Returns 0 if the next pending task is due now.
     */
    private long timeToNextTask() {
        Scheduled head;
        while ((head = scheduled.peek()) != null) {
            if (!head.isPending()) {
                scheduled.poll();
                continue;
            }
            return Math.max(0, head.deadline - clock.getAsLong());
        }
        return -1;
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

    /** The lifecycle state of a scheduled task. */
    private enum State {
        PENDING,
        CANCELLED,
        RUNNING
    }

    /** A handle to a scheduled task, as returned by the "schedule" method. */
    public static final class Task {
        private final Scheduled task;

        private Task(Scheduled task) {
            this.task = task;
        }

        /**
         * Cancel the task if it has not yet run. If this method returns true, the task will never
         * run.
         *
         * @return true if the task was cancelled, false if it was already cancelled or has already
         *     been picked up to run
         */
        public boolean cancel() {
            return task.cancel();
        }
    }

    /** A scheduled task, kept in deadline order. */
    private static final class Scheduled implements Comparable<Scheduled> {
        final Consumer<Context> task;
        final long deadline;
        final long seq;
        State state = State.PENDING;

        Scheduled(Consumer<Context> task, long deadline, long seq) {
            this.task = task;
            this.deadline = deadline;
            this.seq = seq;
        }

        @Override
        public int compareTo(Scheduled o) {
            int c = Long.compare(deadline, o.deadline);
            return c != 0 ? c : Long.compare(seq, o.seq);
        }

        /** Cancel the task if it has not yet run. */
        boolean cancel() {
            if (state != State.PENDING) {
                return false;
            }
            state = State.CANCELLED;
            return true;
        }

        /**
         * Mark this task as picked up to run so that a late cancel does not claim success. The task
         * is still delivered through the microtask queue.
         */
        void beginRun() {
            state = State.RUNNING;
        }

        boolean isPending() {
            return state == State.PENDING;
        }
    }
}
