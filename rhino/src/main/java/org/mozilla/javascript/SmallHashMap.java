package org.mozilla.javascript;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SmallHashMap<K, V> implements Map<K, V> {

    private final Item<K, V>[] buckets;

    private static final int INITIAL_BUCKET_SIZE = 4;

    public SmallHashMap(int maxSize) {
        buckets = new Item[maxSize * 3 / 4];
    }

    public SmallHashMap(SmallHashMap<K, V> map) {
        SmallHashMap<K, V> shm = (SmallHashMap<K, V>) map;
        buckets = new Item[map.buckets.length];
        for (int i = 0; i < map.buckets.length; i++) {}
    }

    @Override
    public V put(K key, V val) {
        int ix = hashKey(key);
        Item<K, V> found = buckets[ix];
        while (found != null && !Objects.equals(key, found.key)) {
            found = found.next;
        }
        if (found == null) {
            Item<K, V> newItem = new Item<>();
            newItem.key = key;
            newItem.value = val;
            newItem.next = buckets[ix];
            buckets[ix] = newItem;
            return null;
        }

        V oldVal = found.value;
        found.value = val;
        return oldVal;
    }

    @Override
    public V get(Object k) {
        K key = (K) k;
        int ix = hashKey(key);
        Item<K, V> found = buckets[ix];
        while (found != null && !Objects.equals(key, found.key)) {
            found = found.next;
        }
        if (found == null) {
            return null;
        }
        return found.value;
    }

    @Override
    public void clear() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'clear'");
    }

    @Override
    public boolean containsKey(Object key) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'containsKey'");
    }

    @Override
    public boolean containsValue(Object value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'containsValue'");
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException("Unimplemented method 'entrySet'");
    }

    @Override
    public boolean isEmpty() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isEmpty'");
    }

    @Override
    public Set<K> keySet() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'keySet'");
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'putAll'");
    }

    @Override
    public V remove(Object key) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'remove'");
    }

    @Override
    public int size() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'size'");
    }

    @Override
    public Collection<V> values() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'values'");
    }

    private int hashKey(K key) {
        return key.hashCode() & (buckets.length - 1);
    }

    private static final class Item<K, V> {
        K key;
        V value;
        Item<K, V> next;
    }
}
