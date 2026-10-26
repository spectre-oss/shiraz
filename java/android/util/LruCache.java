// SPDX-License-Identifier: GPL-3.0-only
package android.util;
import java.util.*;

/** Access-ordered cache with caller-defined weights and callbacks outside its monitor. */
public class LruCache<K,V> {
    private final LinkedHashMap<K,V> values = new LinkedHashMap<K,V>(0,.75f,true);
    private long size;
    private int maximum, hits, misses, creates, puts, evictions;
    public LruCache(int maxSize) { if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive"); maximum = maxSize; }
    public void resize(int maxSize) { if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive"); synchronized (this) { maximum = maxSize; } trimToSize(maxSize); }
    private int weight(K key, V value) { int result = sizeOf(key,value); if (result < 0) throw new IllegalStateException("Cache sizeOf returned a negative weight"); return result; }
    private void consistent() { if (size < 0 || values.isEmpty() && size != 0) throw new IllegalStateException("Cache sizeOf reports inconsistent weights"); }
    public final V get(K key) {
        Objects.requireNonNull(key,"key"); V existing;
        synchronized (this) { existing = values.get(key); if (existing != null) { ++hits; return existing; } ++misses; }
        V created = create(key); if (created == null) return null;
        synchronized (this) {
            ++creates; existing = values.get(key);
            if (existing == null) { int amount = weight(key,created); long next = Math.addExact(size,amount); values.put(key,created); size = next; }
        }
        if (existing != null) { entryRemoved(false,key,created,existing); return existing; }
        trimToSize(maxSize()); return created;
    }
    public final V put(K key, V value) {
        Objects.requireNonNull(key,"key"); Objects.requireNonNull(value,"value"); V previous;
        synchronized (this) {
            ++puts; int added = weight(key,value); previous = values.get(key);
            long next = Math.addExact(size,added) - (previous == null ? 0 : weight(key,previous));
            if (next < 0) throw new IllegalStateException("Cache sizeOf reports inconsistent weights");
            values.put(key,value); size = next;
        }
        if (previous != null) entryRemoved(false,key,previous,value);
        trimToSize(maxSize()); return previous;
    }
    public void trimToSize(int maxSize) {
        for (;;) {
            K key; V value;
            synchronized (this) {
                consistent(); if (size <= maxSize || values.isEmpty()) return;
                Iterator<Map.Entry<K,V>> iterator = values.entrySet().iterator(); Map.Entry<K,V> eldest = iterator.next(); key = eldest.getKey(); value = eldest.getValue();
                long next = size - weight(key,value);
                if (next < 0 || values.size() == 1 && next != 0) throw new IllegalStateException("Cache sizeOf reports inconsistent weights");
                iterator.remove(); size = next; ++evictions;
            }
            entryRemoved(true,key,value,null);
        }
    }
    public final V remove(K key) {
        Objects.requireNonNull(key,"key"); V previous;
        synchronized (this) {
            previous = values.get(key);
            if (previous != null) {
                long next = size - weight(key,previous);
                if (next < 0 || values.size() == 1 && next != 0) throw new IllegalStateException("Cache sizeOf reports inconsistent weights");
                values.remove(key); size = next;
            }
        }
        if (previous != null) entryRemoved(false,key,previous,null); return previous;
    }
    protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) { }
    protected V create(K key) { return null; }
    protected int sizeOf(K key, V value) { return 1; }
    public final void evictAll() { trimToSize(-1); }
    public final synchronized int size() { if (size > Integer.MAX_VALUE) throw new IllegalStateException("Transient cache weight exceeds public int size range"); return (int)size; }
    public final synchronized int maxSize() { return maximum; }
    public final synchronized int hitCount() { return hits; }
    public final synchronized int missCount() { return misses; }
    public final synchronized int createCount() { return creates; }
    public final synchronized int putCount() { return puts; }
    public final synchronized int evictionCount() { return evictions; }
    public final synchronized Map<K,V> snapshot() { return new LinkedHashMap<K,V>(values); }
    @Override public final synchronized String toString() { long accesses = (long)hits + misses; return "LruCache[maxSize=" + maximum + ",hits=" + hits + ",misses=" + misses + ",hitRate=" + (accesses == 0 ? 0 : hits * 100L / accesses) + "%]"; }
}
