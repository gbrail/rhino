package org.mozilla.javascript;

import java.util.Objects;
import java.util.function.Function;

public class EmbeddedMap<K, V> {
    private static final int INITIAL_SIZE = 4;

    private Entry<K, V>[] slots;
    private int count;

    @SuppressWarnings("unchecked")
    public EmbeddedMap() {
        this.slots = new Entry[INITIAL_SIZE];
    }

    @SuppressWarnings("unchecked")
    public EmbeddedMap(EmbeddedMap<K, V> m) {
        this.slots = new Entry[m.slots.length];
        this.count = m.count;
        for (int i = 0; i < slots.length; i++) {
            Entry<K, V> origEntry = m.slots[i];
            while (origEntry != null) {
                Entry<K, V> e = new Entry<>(origEntry);
                Entry<K, V> old = slots[i];
                slots[i] = e;
                e.next = old;
                origEntry = origEntry.next;
            }
        }
    }

    public int size() {
        return count;
    }

    public void put(K key, V value) {
        int slotIndex = getSlotIndex(slots.length, key.hashCode());
        Entry<K, V> e;
        for (e = slots[slotIndex]; e != null; e = e.next) {
            if (Objects.equals(e.key, key)) {
                break;
            }
        }
        if (e != null) {
            e.value = value;
            return;
        }

        Entry<K, V> newEntry = new Entry<>(key, value);
        createNewEntry(newEntry, slotIndex);
    }

    public V computeIfAbsent(K key, Function<K, V> f) {
        int slotIndex = getSlotIndex(slots.length, key.hashCode());
        for (Entry<K, V> e = slots[slotIndex]; e != null; e = e.next) {
            if (Objects.equals(e.key, key)) {
                return e.value;
            }
        }

        V value = f.apply(key);
        Entry<K, V> newEntry = new Entry<>(key, value);
        createNewEntry(newEntry, slotIndex);
        return value;
    }

    public V get(K key) {
        int hashCode = key.hashCode();
        int slotIndex = getSlotIndex(slots.length, hashCode);
        for (Entry<K, V> e = slots[slotIndex]; e != null; e = e.next) {
            if (Objects.equals(e.key, key)) {
                return e.value;
            }
        }
        return null;
    }

    private void createNewEntry(Entry<K, V> e, int slotIx) {
        if (4 * (count + 1) > 3 * slots.length) {
            // table size must be a power of 2 -- always grow by x2!
            @SuppressWarnings("unchecked")
            Entry<K, V>[] newSlots = new Entry[slots.length * 2];
            copyTable(slots, newSlots);
            slotIx = getSlotIndex(newSlots.length, e.key.hashCode());
            slots = newSlots;
        }

        addKnownAbsentSlot(slots, e, slotIx);
        count++;
    }

    private void addKnownAbsentSlot(Entry<K, V>[] addSlots, Entry<K, V> e, int slotIx) {
        Entry<K, V> old = addSlots[slotIx];
        addSlots[slotIx] = e;
        e.next = old;
    }

    private void copyTable(Entry<K, V>[] oldSlots, Entry<K, V>[] newSlots) {
        for (Entry<K, V> e : oldSlots) {
            while (e != null) {
                Entry<K, V> next = e.next;
                int slotIx = getSlotIndex(newSlots.length, e.key.hashCode());
                addKnownAbsentSlot(newSlots, e, slotIx);
                e = next;
            }
        }
    }

    private static int getSlotIndex(int tableSize, int indexOrHash) {
        // This is a Java trick to efficiently "mod" the hash code by the table size.
        // It only works if the table size is a power of 2.
        return indexOrHash & (tableSize - 1);
    }

    private static final class Entry<K, V> {
        final K key;
        V value;
        Entry<K, V> next;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        Entry(Entry<K, V> e) {
            this.key = e.key;
            this.value = e.value;
        }
    }
}
