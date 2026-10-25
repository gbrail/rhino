package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
        assertEquals(Long.MAX_VALUE, queue.timeToNextTask());
        assertFalse(queue.processPending());
    }

    @Test
    public void scheduledTaskOrdering() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock(1000);
        queue.setClock(clock);
        List<String> order = new ArrayList<>();
        queue.schedule(c -> order.add("ten"), 10);
        queue.schedule(c -> order.add("zero"));
        queue.schedule(c -> order.add("five"), 5);
        clock.advance(5);
        queue.processPending();
        assertEquals(List.of("zero", "five"), order);
        clock.advance(5);
        queue.processPending();
        assertEquals(List.of("zero", "five", "ten"), order);
        clock.advance(5);
        queue.processPending();
        assertEquals(List.of("zero", "five", "ten"), order);
        assertEquals(Long.MAX_VALUE, queue.timeToNextTask());
    }

    @Test
    public void equalDeadlinesRunInOrder() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        List<String> order = new ArrayList<>();
        queue.schedule(c -> order.add("first"), 10);
        queue.schedule(c -> order.add("second"), 10);
        queue.schedule(c -> order.add("third"), 10);
        clock.advance(10);
        queue.processPending();
        assertEquals(List.of("first", "second", "third"), order);
    }

    @Test
    public void taskNotRunBeforeDeadline() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock(0);
        queue.setClock(clock);
        AtomicBoolean ran = new AtomicBoolean();
        queue.schedule(c -> ran.set(true), 100);
        assertEquals(100, queue.timeToNextTask());
        assertFalse(queue.processPending());
        assertFalse(ran.get());
        clock.advance(100);
        assertEquals(0, queue.timeToNextTask());
        assertTrue(queue.processPending());
        assertTrue(ran.get());
    }

    @Test
    public void negativeDelayRunsImmediately() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        AtomicBoolean ran = new AtomicBoolean();
        queue.schedule(c -> ran.set(true), -50);
        assertTrue(queue.processPending());
        assertTrue(ran.get());
    }

    @Test
    public void taskCancellation() {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        AtomicBoolean first = new AtomicBoolean();
        AtomicBoolean second = new AtomicBoolean();
        TaskQueue.Task t1 = queue.schedule(c -> first.set(true), 10);
        queue.schedule(c -> second.set(true), 20);
        assertEquals(10, queue.timeToNextTask());
        assertTrue(t1.cancel());
        assertFalse(t1.cancel());
        clock.advance(20);
        queue.processPending();
        assertFalse(first.get());
        assertTrue(second.get());
        assertEquals(Long.MAX_VALUE, queue.timeToNextTask());
    }

    /** A task that has already run can no longer be cancelled. */
    @Test
    public void cancelAfterRunReturnsFalse() throws InterruptedException {
        TaskQueue queue = cx.getTaskQueue();
        FakeClock clock = new FakeClock();
        queue.setClock(clock);
        AtomicBoolean ran = new AtomicBoolean();
        TaskQueue.Task task = queue.schedule(c -> ran.set(true), 10);
        clock.advance(10);
        assertTrue(queue.processPending());
        assertTrue(ran.get());
        assertFalse(task.cancel());
        // Same for a task run through awaitNextTask
        TaskQueue.Task task2 = queue.schedule(c -> {}, 0);
        assertTrue(queue.awaitNextTask(1000));
        assertFalse(task2.cancel());
    }

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
                1);
        queue.schedule(c -> order.add("task2"), 2);
        clock.advance(2);
        queue.processPending();
        assertEquals(List.of("task1", "microtask", "task2"), order);
    }

    @Test
    public void processPendingWithSuspendedMicrotasks() {
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
                1);
        clock.advance(1);
        queue.processPending();
        assertTrue(task.get());
        assertFalse(microtask.get());
        queue.resumeMicrotaskProcessing();
        assertTrue(microtask.get());
    }

    @Test
    public void awaitNextTask() throws InterruptedException {
        TaskQueue queue = cx.getTaskQueue();
        // Nothing scheduled: no wait, no task
        assertFalse(queue.awaitNextTask(0));
        // A task that is not due yet: timeout 0 returns without running it
        AtomicBoolean ran = new AtomicBoolean();
        queue.schedule(c -> ran.set(true), 100);
        assertFalse(queue.awaitNextTask(0));
        assertFalse(ran.get());
        // A task due soon: the wait is bounded by the deadline
        queue.schedule(c -> ran.set(true), 20);
        assertTrue(queue.awaitNextTask(500));
        assertTrue(ran.get());
    }

    @Test
    public void postFromOtherThreadRunsOnOwnerThread() throws Exception {
        TaskQueue queue = cx.getTaskQueue();
        Thread owner = Thread.currentThread();
        AtomicReference<Thread> ranOn = new AtomicReference<>();
        AtomicReference<Context> receivedContext = new AtomicReference<>();
        AtomicBoolean ran = new AtomicBoolean();
        runOnOtherThread(
                () ->
                        queue.post(
                                c -> {
                                    ranOn.set(Thread.currentThread());
                                    receivedContext.set(c);
                                    ran.set(true);
                                }));
        assertTrue(queue.awaitNextTask(5000));
        assertTrue(ran.get());
        assertSame(owner, ranOn.get());
        // The task receives the context, which is set on the thread when it runs
        assertSame(cx, receivedContext.get());
        assertSame(cx, Context.getCurrentContext());
    }

    @Test
    public void postFromOtherThreadWakesAwait() throws Exception {
        TaskQueue queue = cx.getTaskQueue();
        AtomicBoolean ran = new AtomicBoolean();
        Thread poster =
                new Thread(
                        () -> {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                return;
                            }
                            queue.post(c -> ran.set(true));
                        });
        poster.start();
        long start = System.nanoTime();
        assertTrue(queue.awaitNextTask(5000));
        long elapsedMs = (System.nanoTime() - start) / 1000000;
        poster.join();
        assertTrue(ran.get());
        // Should have been woken by the post (~100ms), not by the 5000ms timeout
        assertTrue(elapsedMs < 2500, "elapsed=" + elapsedMs);
    }

    @Test
    public void crossThreadCancellation() throws Exception {
        TaskQueue queue = cx.getTaskQueue();
        AtomicBoolean ran = new AtomicBoolean();
        AtomicReference<TaskQueue.Task> handle = new AtomicReference<>();
        runOnOtherThread(() -> handle.set(queue.post(c -> ran.set(true), 60000)));
        assertTrue(handle.get().cancel());
        assertEquals(Long.MAX_VALUE, queue.timeToNextTask());
        assertFalse(queue.processPending());
        assertFalse(ran.get());
    }

    @Test
    public void manyThreadsPost() throws Exception {
        TaskQueue queue = cx.getTaskQueue();
        int threads = 8;
        int perThread = 25;
        AtomicInteger count = new AtomicInteger();
        AtomicBoolean allOnOwner = new AtomicBoolean(true);
        Thread owner = Thread.currentThread();
        List<Thread> posters = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread t =
                    new Thread(
                            () -> {
                                for (int j = 0; j < perThread; j++) {
                                    queue.post(
                                            c -> {
                                                if (Thread.currentThread() != owner) {
                                                    allOnOwner.set(false);
                                                }
                                                count.incrementAndGet();
                                            });
                                }
                            });
            posters.add(t);
            t.start();
        }
        for (Thread t : posters) {
            t.join();
        }
        // All posts are complete; drain until empty
        while (queue.timeToNextTask() != Long.MAX_VALUE) {
            queue.processPending();
        }
        assertEquals(threads * perThread, count.get());
        assertTrue(allOnOwner.get());
    }

    private static void runOnOtherThread(Runnable r) throws InterruptedException {
        Thread t = new Thread(r);
        t.start();
        t.join();
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
