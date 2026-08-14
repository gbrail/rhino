package org.mozilla.javascript.typedarrays;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class WaitersTest {

    @Test
    public void resultToString_ok() {
        assertEquals("ok", Waiters.resultToString(Waiters.Result.OK));
    }

    @Test
    public void resultToString_notEqual() {
        assertEquals("not-equal", Waiters.resultToString(Waiters.Result.NOT_EQUAL));
    }

    @Test
    public void resultToString_timedOut() {
        assertEquals("timed-out", Waiters.resultToString(Waiters.Result.TIMED_OUT));
    }

    @Test
    public void waitSync_notEqual() {
        Waiters waiters = new Waiters();
        Waiters.Result result = waiters.waitSync(0, 10, () -> false);
        assertEquals(Waiters.Result.NOT_EQUAL, result);
    }

    @Test
    public void waitSync_notEqual_doesNotEnqueue() {
        Waiters waiters = new Waiters();
        waiters.waitSync(0, 10, () -> false);
        assertEquals(0, waiters.notify(0, 1));
    }

    @Test
    public void waitSync_timedOut() throws InterruptedException {
        Waiters waiters = new Waiters();
        long start = System.nanoTime();
        Waiters.Result result = waiters.waitSync(0, 10, () -> true);
        long elapsed = System.nanoTime() - start;

        assertEquals(Waiters.Result.TIMED_OUT, result);
        assertTrue(
                elapsed >= 5_000_000L,
                "Should have waited at least 5ms, got " + elapsed / 1_000_000 + "ms");
    }

    @Test
    public void waitSync_timeoutZero_returnsImmediately() {
        Waiters waiters = new Waiters();
        Waiters.Result result = waiters.waitSync(0, 0, () -> true);
        assertEquals(Waiters.Result.TIMED_OUT, result);
    }

    @Test
    public void notify_emptyIndex_returnsZero() {
        Waiters waiters = new Waiters();
        assertEquals(0, waiters.notify(0, 1));
    }

    @Test
    public void notify_afterTimeout_returnsZero() throws InterruptedException {
        Waiters waiters = new Waiters();
        waiters.waitSync(0, 5, () -> true);
        assertEquals(0, waiters.notify(0, 1));
    }

    @Test
    public void waitSync_ok_whenNotified() throws InterruptedException, TimeoutException {
        Waiters waiters = new Waiters();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch notified = new CountDownLatch(1);
        AtomicBoolean eq = new AtomicBoolean(true);

        Thread waiter =
                new Thread(
                        () -> {
                            started.countDown();
                            Waiters.Result result = waiters.waitSync(0, 5000, eq::get);
                            assertEquals(Waiters.Result.OK, result);
                            notified.countDown();
                        });
        waiter.start();

        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.sleep(50);
        int count = waiters.notify(0, 1);
        assertEquals(1, count);

        assertTrue(notified.await(1, TimeUnit.SECONDS));
        waiter.join(1000);
    }

    @Test
    public void waitSync_ok_whenNotifiedBeforePark() throws InterruptedException, TimeoutException {
        // Notify is called between the compute and the park -- LockSupport permit should handle it
        Waiters waiters = new Waiters();
        AtomicInteger state = new AtomicInteger(0);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch notified = new CountDownLatch(1);

        Thread waiter =
                new Thread(
                        () -> {
                            started.countDown();
                            Waiters.Result result =
                                    waiters.waitSync(0, 5000, () -> state.get() == 0);
                            assertEquals(Waiters.Result.OK, result);
                            notified.countDown();
                        });
        waiter.start();

        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);
        state.set(1);
        int count = waiters.notify(0, 1);
        assertEquals(1, count);

        assertTrue(notified.await(1, TimeUnit.SECONDS));
        waiter.join(1000);
    }

    @Test
    public void notify_multipleWaiters() throws InterruptedException, TimeoutException {
        Waiters waiters = new Waiters();
        CountDownLatch started = new CountDownLatch(3);
        CountDownLatch notified = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            new Thread(
                            () -> {
                                started.countDown();
                                Waiters.Result result = waiters.waitSync(0, 5000, () -> true);
                                assertEquals(Waiters.Result.OK, result);
                                notified.countDown();
                            })
                    .start();
        }

        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.sleep(50);
        int count = waiters.notify(0, 3);
        assertEquals(3, count);

        assertTrue(notified.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void notify_countLimit() throws InterruptedException, TimeoutException {
        Waiters waiters = new Waiters();
        AtomicInteger notifiedCount = new AtomicInteger(0);
        CountDownLatch notifiedLatch = new CountDownLatch(2);
        CountDownLatch allStarted = new CountDownLatch(4);

        for (int i = 0; i < 4; i++) {
            new Thread(
                            () -> {
                                allStarted.countDown();
                                Waiters.Result result = waiters.waitSync(0, 5000, () -> true);
                                if (result == Waiters.Result.OK) {
                                    notifiedCount.incrementAndGet();
                                    notifiedLatch.countDown();
                                }
                            })
                    .start();
        }

        assertTrue(allStarted.await(1, TimeUnit.SECONDS));
        // Poll until all 4 waiters are enqueued
        for (int i = 0; i < 200 && waiters.waiterCount(0) < 4; i++) {
            Thread.sleep(5);
        }
        assertEquals(4, waiters.waiterCount(0));

        // notify with count=2: should wake exactly 2
        int count = waiters.notify(0, 2);
        assertEquals(2, count);

        // 2 should be notified, 2 should remain in the queue
        assertEquals(2, waiters.waiterCount(0));

        // Wait for the 2 notified threads to finish
        assertTrue(notifiedLatch.await(2, TimeUnit.SECONDS));
        assertEquals(2, notifiedCount.get());

        // Remaining 2 will time out; clean them up with notify(0, 0) or wait
        // Notify the remaining 2 so they don't hang
        waiters.notify(0, 2);
    }

    @Test
    public void waitSync_differentIndices_areIndependent() throws InterruptedException {
        Waiters waiters = new Waiters();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch notified = new CountDownLatch(1);

        Thread waiter =
                new Thread(
                        () -> {
                            started.countDown();
                            Waiters.Result result = waiters.waitSync(0, 5000, () -> true);
                            assertEquals(Waiters.Result.OK, result);
                            notified.countDown();
                        });
        waiter.start();

        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.sleep(50);
        assertEquals(0, waiters.notify(1, 1));
        assertEquals(1, waiters.notify(0, 1));

        assertTrue(notified.await(1, TimeUnit.SECONDS));
        waiter.join(1000);
    }
}
