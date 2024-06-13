package org.mozilla.javascript;

import java.util.concurrent.locks.StampedLock;
import java.util.function.BiFunction;

public class ThreadSafeHashSlotMap extends HashSlotMap {
    private final StampedLock lock = new StampedLock();

    @Override
    public int size() {
        long stamp = lock.tryOptimisticRead();
        int s = super.size();
        if (lock.validate(stamp)) {
            return s;
        }

        stamp = lock.readLock();
        try {
            return super.size();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean has(Slot.Key key) {
        long stamp = lock.tryOptimisticRead();
        boolean h = super.has(key);
        if (lock.validate(stamp)) {
            return h;
        }

        stamp = lock.readLock();
        try {
            return super.has(key);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public Slot query(Slot.Key key) {
        long stamp = lock.tryOptimisticRead();
        Slot s = super.query(key);
        if (lock.validate(stamp)) {
            return s;
        }

        stamp = lock.readLock();
        try {
            return super.query(key);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public Slot modify(Slot.Key key, int attributes) {
        long stamp = lock.writeLock();
        try {
            return super.modify(key, attributes);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public <S extends Slot> S compute(Slot.Key key, BiFunction<Slot.Key, Slot, S> c) {
        long stamp = lock.writeLock();
        try {
            return super.compute(key, c);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void add(Slot newSlot) {
        long stamp = lock.writeLock();
        try {
            super.add(newSlot);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void remove(Slot.Key key) {
        long stamp = lock.writeLock();
        try {
            super.remove(key);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public long readLock() {
        return lock.readLock();
    }

    @Override
    public void unlockRead(long stamp) {
        lock.unlockRead(stamp);
    }
}
