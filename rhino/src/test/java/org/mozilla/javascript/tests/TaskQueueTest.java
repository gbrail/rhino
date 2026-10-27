package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.TaskQueue;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.VarScope;

public class TaskQueueTest {
    private Context cx;
    private VarScope scope;

    @BeforeEach
    public void init() {
        cx = Context.enter();
        scope = cx.initStandardObjects();
    }

    @AfterEach
    public void cleanup() {
        Context.exit();
    }

    @Test
    public void oneQueuePerContext() {
        TaskQueue queue = cx.getTaskQueue();
        assertSame(queue, cx.getTaskQueue());
        Context.exit();
        Context other = Context.enter();
        assertNotSame(queue, other.getTaskQueue());
    }

    @Test
    public void delegatesShareState() {
        // The Context convenience methods and the task queue must operate on the same state.
        assertSame(cx.getUnhandledPromiseTracker(), cx.getTaskQueue().getUnhandledPromiseTracker());
        AtomicBoolean done = new AtomicBoolean();
        cx.enqueueMicrotask(() -> done.set(true));
        cx.getTaskQueue().processMicrotasks();
        assertTrue(done.get());
    }

    @Test
    public void enqueueAndProcessMicrotask() {
        TaskQueue queue = cx.getTaskQueue();
        AtomicBoolean done = new AtomicBoolean();
        queue.enqueueMicrotask(() -> done.set(true));
        assertFalse(done.get());
        queue.processMicrotasks();
        assertTrue(done.get());
        // Processing again is a no-op
        queue.processMicrotasks();
    }

    @Test
    public void suspendAndResume() {
        TaskQueue queue = cx.getTaskQueue();
        queue.suspendMicrotaskProcessing();
        AtomicBoolean done = new AtomicBoolean();
        queue.enqueueMicrotask(() -> done.set(true));
        queue.processMicrotasks();
        assertFalse(done.get());
        queue.resumeMicrotaskProcessing();
        assertTrue(done.get());
        // Cumulative suspension
        queue.suspendMicrotaskProcessing();
        queue.suspendMicrotaskProcessing();
        AtomicBoolean done2 = new AtomicBoolean();
        queue.enqueueMicrotask(() -> done2.set(true));
        queue.resumeMicrotaskProcessing();
        assertFalse(done2.get());
        queue.resumeMicrotaskProcessing();
        assertTrue(done2.get());
    }

    @Test
    public void unhandledPromiseTracking() {
        TaskQueue queue = cx.getTaskQueue();
        queue.setTrackUnhandledPromiseRejections(true);
        cx.evaluateString(scope, "Promise.reject(undefined);", "test.js", 1, null);
        queue.processMicrotasks();
        assertEquals(1, queue.getUnhandledPromiseTracker().enumerate().size());
        assertTrue(Undefined.isUndefined(queue.getUnhandledPromiseTracker().enumerate().get(0)));
        queue.getUnhandledPromiseTracker().process(r -> {});
        assertTrue(queue.getUnhandledPromiseTracker().enumerate().isEmpty());
    }

    @Test
    public void finalizationFlag() {
        TaskQueue queue = cx.getTaskQueue();
        assertFalse(queue.isFinalizationEnabled());
        assertFalse(cx.isFinalizationEnabled());
        queue.setFinalizationEnabled(true);
        assertTrue(queue.isFinalizationEnabled());
        // The Context delegate must observe the same state
        assertTrue(cx.isFinalizationEnabled());
    }

    @Test
    public void emptyQueue() {
        TaskQueue queue = cx.getTaskQueue();
        assertEquals(-1, queue.runTasks());
        assertEquals(-1, queue.runTasks());
    }

    @Test
    public void scheduledTaskOrdering() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock(1000);
        queue.setClock(clock);
        List<String> order = new ArrayList<>();
        queue.schedule(c -> order.add("ten"), 10, TimeUnit.MILLISECONDS);
        queue.schedule(c -> order.add("zero"), 0, TimeUnit.MILLISECONDS);
        queue.schedule(c -> order.add("five"), 5, TimeUnit.MILLISECONDS);
        assertEquals(5, queue.runTasks());
        assertEquals(List.of("zero"), order);
        clock.advance(5);
        assertEquals(5, queue.runTasks());
        assertEquals(List.of("zero", "five"), order);
        clock.advance(5);
        assertEquals(-1, queue.runTasks());
        assertEquals(List.of("zero", "five", "ten"), order);
        assertEquals(-1, queue.runTasks());
    }

    @Test
    public void equalDeadlinesRunInOrder() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        List<String> order = new ArrayList<>();
        queue.schedule(c -> order.add("first"), 10, TimeUnit.MILLISECONDS);
        queue.schedule(c -> order.add("second"), 10, TimeUnit.MILLISECONDS);
        queue.schedule(c -> order.add("third"), 10, TimeUnit.MILLISECONDS);
        clock.advance(10);
        assertEquals(-1, queue.runTasks());
        assertEquals(List.of("first", "second", "third"), order);
    }

    @Test
    public void taskNotRunBeforeDeadline() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock(0);
        queue.setClock(clock);
        AtomicBoolean ran = new AtomicBoolean();
        queue.schedule(c -> ran.set(true), 100, TimeUnit.MILLISECONDS);
        assertEquals(100, queue.runTasks());
        assertFalse(ran.get());
        clock.advance(100);
        assertEquals(-1, queue.runTasks());
        assertTrue(ran.get());
    }

    @Test
    public void delayInOtherTimeUnits() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        AtomicBoolean ran = new AtomicBoolean();
        queue.schedule(c -> ran.set(true), 1, TimeUnit.SECONDS);
        assertEquals(1000, queue.runTasks());
        clock.advance(999);
        assertEquals(1, queue.runTasks());
        clock.advance(1);
        assertEquals(-1, queue.runTasks());
        assertTrue(ran.get());
    }

    /**
     * A delay whose conversion to milliseconds saturates (TimeUnit.toMillis returns Long.MAX_VALUE)
     * is never due, and in particular does not run immediately.
     */
    @Test
    public void saturatingDelayNeverRuns() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        AtomicBoolean ran = new AtomicBoolean();
        queue.schedule(c -> ran.set(true), Long.MAX_VALUE, TimeUnit.DAYS);
        assertEquals(Long.MAX_VALUE, queue.runTasks());
        assertFalse(ran.get());
    }

    /** A delay whose deadline would overflow is clamped to Long.MAX_VALUE. */
    @Test
    public void overflowingDelayNeverRuns() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock(Long.MAX_VALUE - 10);
        queue.setClock(clock);
        AtomicBoolean ran = new AtomicBoolean();
        queue.schedule(c -> ran.set(true), 100, TimeUnit.MILLISECONDS);
        assertEquals(10, queue.runTasks());
        assertFalse(ran.get());
    }

    @Test
    public void negativeDelayRunsImmediately() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        AtomicBoolean ran = new AtomicBoolean();
        queue.schedule(c -> ran.set(true), -50, TimeUnit.MILLISECONDS);
        assertEquals(-1, queue.runTasks());
        assertTrue(ran.get());
    }

    @Test
    public void taskCancellation() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        AtomicBoolean first = new AtomicBoolean();
        AtomicBoolean second = new AtomicBoolean();
        TaskQueue.Task t1 = queue.schedule(c -> first.set(true), 10, TimeUnit.MILLISECONDS);
        queue.schedule(c -> second.set(true), 20, TimeUnit.MILLISECONDS);
        assertEquals(10, queue.runTasks());
        assertTrue(t1.cancel());
        assertFalse(t1.cancel());
        clock.advance(20);
        assertEquals(-1, queue.runTasks());
        assertFalse(first.get());
        assertTrue(second.get());
        assertEquals(-1, queue.runTasks());
    }

    /** A task cancelled from a running task's callback does not run and is purged. */
    @Test
    public void cancelFromRunningTask() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        AtomicBoolean first = new AtomicBoolean();
        AtomicBoolean second = new AtomicBoolean();
        TaskQueue.Task t2 = queue.schedule(c -> second.set(true), 20, TimeUnit.MILLISECONDS);
        queue.schedule(
                c -> {
                    first.set(true);
                    assertTrue(t2.cancel());
                },
                10,
                TimeUnit.MILLISECONDS);
        clock.advance(20);
        assertEquals(-1, queue.runTasks());
        assertTrue(first.get());
        assertFalse(second.get());
        assertEquals(-1, queue.runTasks());
    }

    /** A task that has already run can no longer be cancelled. */
    @Test
    public void cancelAfterRunReturnsFalse() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        AtomicBoolean ran = new AtomicBoolean();
        TaskQueue.Task task = queue.schedule(c -> ran.set(true), 10, TimeUnit.MILLISECONDS);
        clock.advance(10);
        assertEquals(-1, queue.runTasks());
        assertTrue(ran.get());
        assertFalse(task.cancel());
    }

    /** A zero-delay timer scheduled by a timer runs in the same call to runTasks. */
    @Test
    public void rescheduledImmediateTimerRunsInSameCall() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        List<String> order = new ArrayList<>();
        queue.schedule(
                c -> {
                    order.add("first");
                    queue.schedule(c2 -> order.add("second"), 0, TimeUnit.MILLISECONDS);
                },
                0,
                TimeUnit.MILLISECONDS);
        assertEquals(-1, queue.runTasks());
        assertEquals(List.of("first", "second"), order);
    }

    /** A timer scheduled during a run updates the returned delay if it is sooner. */
    @Test
    public void rescheduledTimerShortensNextDelay() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        List<String> order = new ArrayList<>();
        queue.schedule(
                c -> {
                    order.add("first");
                    queue.schedule(c2 -> order.add("second"), 5, TimeUnit.MILLISECONDS);
                },
                0,
                TimeUnit.MILLISECONDS);
        queue.schedule(c -> order.add("late"), 100, TimeUnit.MILLISECONDS);
        assertEquals(5, queue.runTasks());
        assertEquals(List.of("first"), order);
        clock.advance(5);
        assertEquals(95, queue.runTasks());
        assertEquals(List.of("first", "second"), order);
        clock.advance(95);
        assertEquals(-1, queue.runTasks());
        assertEquals(List.of("first", "second", "late"), order);
    }

    /** A microtask scheduled by a timer runs before the next timer, as in the browser. */
    @Test
    public void microtaskCheckpointBetweenTasks() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        List<String> order = new ArrayList<>();
        queue.schedule(
                c -> {
                    order.add("task1");
                    queue.enqueueMicrotask(() -> order.add("microtask"));
                },
                1,
                TimeUnit.MILLISECONDS);
        queue.schedule(c -> order.add("task2"), 2, TimeUnit.MILLISECONDS);
        clock.advance(2);
        assertEquals(-1, queue.runTasks());
        assertEquals(List.of("task1", "microtask", "task2"), order);
    }

    @Test
    public void runTasksWithSuspendedMicrotasks() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        queue.suspendMicrotaskProcessing();
        AtomicBoolean task = new AtomicBoolean();
        AtomicBoolean microtask = new AtomicBoolean();
        queue.schedule(
                c -> {
                    task.set(true);
                    queue.enqueueMicrotask(() -> microtask.set(true));
                },
                1,
                TimeUnit.MILLISECONDS);
        clock.advance(1);
        assertEquals(-1, queue.runTasks());
        assertFalse(task.get());
        assertFalse(microtask.get());
        queue.resumeMicrotaskProcessing();
        assertTrue(task.get());
        assertTrue(microtask.get());
    }

    /** A posted task runs at the next microtask checkpoint, without runTasks. */
    @Test
    public void postedTaskRunsOnProcessMicrotasks() {
        TaskQueue queue = cx.getTaskQueue();
        AtomicBoolean done = new AtomicBoolean();
        queue.post(c -> done.set(true));
        queue.processMicrotasks();
        assertTrue(done.get());
    }

    /** A posted task runs with the queue's context. */
    @Test
    public void postedTaskGetsQueueContext() {
        TaskQueue queue = cx.getTaskQueue();
        AtomicReference<Context> seen = new AtomicReference<>();
        queue.post(seen::set);
        assertEquals(-1, queue.runTasks());
        assertSame(cx, seen.get());
    }

    /** Posted tasks run in the order they were offered. */
    @Test
    public void postedTasksRunInOrder() {
        TaskQueue queue = cx.getTaskQueue();
        List<String> order = new ArrayList<>();
        queue.post(c -> order.add("first"));
        queue.post(c -> order.add("second"));
        queue.post(c -> order.add("third"));
        assertEquals(-1, queue.runTasks());
        assertEquals(List.of("first", "second", "third"), order);
    }

    /** A posted task runs before a later timer, and work posted by a timer runs after it. */
    @Test
    public void postedTaskInterleavesWithTimer() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        List<String> order = new ArrayList<>();
        queue.post(c -> order.add("posted"));
        queue.schedule(
                c -> {
                    order.add("timer");
                    queue.post(c2 -> order.add("posted-later"));
                },
                5,
                TimeUnit.MILLISECONDS);
        assertEquals(5, queue.runTasks());
        assertEquals(List.of("posted"), order);
        clock.advance(5);
        assertEquals(-1, queue.runTasks());
        assertEquals(List.of("posted", "timer", "posted-later"), order);
    }

    /** A posted task is held, without spinning, while microtask processing is suspended. */
    @Test
    public void postedTaskRespectsSuspend() {
        TaskQueue queue = cx.getTaskQueue();
        queue.suspendMicrotaskProcessing();
        AtomicBoolean done = new AtomicBoolean();
        queue.post(c -> done.set(true));
        queue.processMicrotasks();
        assertFalse(done.get());
        assertEquals(-1, queue.runTasks());
        assertFalse(done.get());
        queue.resumeMicrotaskProcessing();
        assertTrue(done.get());
    }

    /** runAllTasks blocks in real time until pending timers have run. */
    @Test
    public void runAllTasksWaitsForTimer() throws InterruptedException {
        TaskQueue queue = cx.getTaskQueue();
        AtomicBoolean ran = new AtomicBoolean();
        queue.schedule(c -> ran.set(true), 20, TimeUnit.MILLISECONDS);
        long start = System.nanoTime();
        queue.runAllTasks();
        long elapsedMs = (System.nanoTime() - start) / 1000000;
        assertTrue(ran.get());
        assertTrue(elapsedMs >= 10, "elapsed=" + elapsedMs);
    }

    /** runAllTasks propagates interrupts so that hosts can stop the pump. */
    @Test
    public void runAllTasksPropagatesInterrupt() {
        TaskQueue queue = cx.getTaskQueue();
        queue.schedule(c -> {}, 60_000, TimeUnit.MILLISECONDS);
        Thread.currentThread().interrupt();
        assertThrows(InterruptedException.class, queue::runAllTasks);
        Thread.interrupted();
    }

    /** A post from another thread wakes a thread blocked in runAllTasks. */
    @Test
    public void postWakesRunAllTasks() throws InterruptedException {
        TaskQueue queue = cx.getTaskQueue();
        AtomicBoolean postedRan = new AtomicBoolean();
        AtomicBoolean timerRan = new AtomicBoolean();
        AtomicLong postedAt = new AtomicLong();
        queue.schedule(c -> timerRan.set(true), 800, TimeUnit.MILLISECONDS);
        Thread pump =
                new Thread(
                        () -> {
                            try {
                                queue.runAllTasks();
                            } catch (InterruptedException e) {
                                throw new AssertionError(e);
                            }
                        },
                        "pump");
        long start = System.nanoTime();
        pump.start();
        Thread.sleep(100);
        queue.post(
                c -> {
                    postedAt.set(System.nanoTime());
                    postedRan.set(true);
                });
        pump.join(2000);
        assertFalse(pump.isAlive());
        assertTrue(postedRan.get());
        assertTrue(timerRan.get());
        // The post must be picked up promptly, long before the 800 ms timer would fire.
        long postedLatencyMs = (postedAt.get() - start) / 1000000;
        assertTrue(postedLatencyMs < 500, "latency=" + postedLatencyMs);
    }

    /** Posting from another thread is safe while the queue is being pumped. */
    @Test
    public void postFromOtherThreadWhilePumping() throws InterruptedException {
        TaskQueue queue = cx.getTaskQueue();
        int count = 10_000;
        AtomicInteger ran = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        // A long timer keeps the pump alive while the posts arrive.
        queue.schedule(c -> {}, 500, TimeUnit.MILLISECONDS);
        Thread pump =
                new Thread(
                        () -> {
                            try {
                                queue.runAllTasks();
                            } catch (Throwable t) {
                                failure.set(t);
                            }
                        },
                        "pump");
        pump.start();
        for (int i = 0; i < count; i++) {
            queue.post(c -> ran.incrementAndGet());
        }
        pump.join(5000);
        assertNull(failure.get());
        assertFalse(pump.isAlive());
        assertEquals(count, ran.get());
    }

    /** A never-due timer does not block posts, and the pump can still be interrupted. */
    @Test
    public void runAllTasksSaturatingTimer() throws InterruptedException {
        TaskQueue queue = cx.getTaskQueue();
        queue.schedule(c -> {}, Long.MAX_VALUE, TimeUnit.DAYS);
        AtomicBoolean postedRan = new AtomicBoolean();
        Thread pump =
                new Thread(
                        () -> {
                            try {
                                queue.runAllTasks();
                            } catch (InterruptedException e) {
                                // Expected at the end of the test.
                            }
                        },
                        "pump");
        pump.start();
        // The pump must block in its wait, not spin on a timer that is never due.
        long deadline = System.currentTimeMillis() + 2000;
        while (pump.getState() != Thread.State.TIMED_WAITING
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(Thread.State.TIMED_WAITING, pump.getState(), "state=" + pump.getState());
        queue.post(c -> postedRan.set(true));
        Thread.sleep(50);
        assertTrue(postedRan.get());
        pump.interrupt();
        pump.join(2000);
        assertFalse(pump.isAlive());
    }

    /** A virtual clock for deterministic testing of scheduled tasks. */
    private static final class FakeClock implements LongSupplier {
        long now;

        FakeClock() {}

        FakeClock(long now) {
            this.now = now;
        }

        @Override
        public long getAsLong() {
            return now;
        }

        void advance(long ms) {
            now += ms;
        }
    }
}
