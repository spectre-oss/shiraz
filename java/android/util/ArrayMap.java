// SPDX-License-Identifier: GPL-3.0-only
package android.util;

import java.lang.reflect.Array;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Memory-efficient generic {@link Map} storing sorted key hashes in an {@code int[]} and
 * interleaved {@code key, value} pairs in an {@code Object[]}.
 */
public final class ArrayMap<K, V> implements Map<K, V> {
    private static final int BASE_SIZE = 4;
    private static final int[] EMPTY_INTS = new int[0];
    private static final Object[] EMPTY_OBJECTS = new Object[0];

    private int[] mHashes;
    private Object[] mArray;
    private int mSize;
    private EntrySet mEntrySet;
    private KeySet mKeySet;
    private ValueCollection mValues;

    public ArrayMap() {
        mHashes = EMPTY_INTS;
        mArray = EMPTY_OBJECTS;
        mSize = 0;
    }

    public ArrayMap(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity < 0: " + capacity);
        }
        if (capacity == 0) {
            mHashes = EMPTY_INTS;
            mArray = EMPTY_OBJECTS;
        } else {
            allocArrays(capacity);
        }
        mSize = 0;
    }

    public ArrayMap(ArrayMap<K, V> map) {
        this();
        if (map != null) {
            putAll(map);
        }
    }

    private void allocArrays(int size) {
        mHashes = new int[size];
        mArray = new Object[size << 1];
    }

    private int binarySearchHashes(int hash) {
        return Arrays.binarySearch(mHashes, 0, mSize, hash);
    }

    private int indexOf(Object key, int hash) {
        int n = mSize;
        if (n == 0) {
            return ~0;
        }
        int index = binarySearchHashes(hash);
        if (index < 0) {
            return index;
        }
        if (Objects.equals(key, mArray[index << 1])) {
            return index;
        }
        int end;
        for (end = index + 1; end < n && mHashes[end] == hash; end++) {
            if (Objects.equals(key, mArray[end << 1])) {
                return end;
            }
        }
        for (int i = index - 1; i >= 0 && mHashes[i] == hash; i--) {
            if (Objects.equals(key, mArray[i << 1])) {
                return i;
            }
        }
        return ~end;
    }

    private int indexOfNull() {
        int n = mSize;
        if (n == 0) {
            return ~0;
        }
        int index = binarySearchHashes(0);
        if (index < 0) {
            return index;
        }
        if (mArray[index << 1] == null) {
            return index;
        }
        int end;
        for (end = index + 1; end < n && mHashes[end] == 0; end++) {
            if (mArray[end << 1] == null) {
                return end;
            }
        }
        for (int i = index - 1; i >= 0 && mHashes[i] == 0; i--) {
            if (mArray[i << 1] == null) {
                return i;
            }
        }
        return ~end;
    }

    @Override
    public void clear() {
        if (mSize > 0) {
            mHashes = EMPTY_INTS;
            mArray = EMPTY_OBJECTS;
            mSize = 0;
        }
    }

    public void ensureCapacity(int minimumCapacity) {
        int osize = mSize;
        if (mHashes.length < minimumCapacity) {
            int[] ohashes = mHashes;
            Object[] oarray = mArray;
            allocArrays(minimumCapacity);
            if (mSize > 0) {
                System.arraycopy(ohashes, 0, mHashes, 0, osize);
                System.arraycopy(oarray, 0, mArray, 0, osize << 1);
            }
        }
        if (mSize != osize) {
            throw new ConcurrentModificationException();
        }
    }

    @Override
    public boolean containsKey(Object key) {
        return indexOfKey(key) >= 0;
    }

    public int indexOfKey(Object key) {
        return key == null ? indexOfNull() : indexOf(key, key.hashCode());
    }

    public int indexOfValue(Object value) {
        int n = mSize * 2;
        Object[] array = mArray;
        if (value == null) {
            for (int i = 1; i < n; i += 2) {
                if (array[i] == null) {
                    return i >> 1;
                }
            }
        } else {
            for (int i = 1; i < n; i += 2) {
                if (value.equals(array[i])) {
                    return i >> 1;
                }
            }
        }
        return -1;
    }

    @Override
    public boolean containsValue(Object value) {
        return indexOfValue(value) >= 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        int index = indexOfKey(key);
        return index >= 0 ? (V) mArray[(index << 1) + 1] : null;
    }

    @SuppressWarnings("unchecked")
    public K keyAt(int index) {
        if (index < 0 || index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return (K) mArray[index << 1];
    }

    @SuppressWarnings("unchecked")
    public V valueAt(int index) {
        if (index < 0 || index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return (V) mArray[(index << 1) + 1];
    }

    @SuppressWarnings("unchecked")
    public V setValueAt(int index, V value) {
        if (index < 0 || index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        int pos = (index << 1) + 1;
        V old = (V) mArray[pos];
        mArray[pos] = value;
        return old;
    }

    @Override
    public boolean isEmpty() {
        return mSize <= 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        final int osize = mSize;
        final int hash;
        int index;
        if (key == null) {
            hash = 0;
            index = indexOfNull();
        } else {
            hash = key.hashCode();
            index = indexOf(key, hash);
        }
        if (index >= 0) {
            index = (index << 1) + 1;
            final V old = (V) mArray[index];
            mArray[index] = value;
            return old;
        }
        index = ~index;
        if (osize >= mHashes.length) {
            final int n =
                    osize >= (BASE_SIZE * 2)
                            ? (osize + (osize >> 1))
                            : (osize >= BASE_SIZE ? (BASE_SIZE * 2) : BASE_SIZE);
            final int[] ohashes = mHashes;
            final Object[] oarray = mArray;
            allocArrays(n);
            if (osize != mSize) {
                throw new ConcurrentModificationException();
            }
            if (mHashes.length > 0) {
                System.arraycopy(ohashes, 0, mHashes, 0, ohashes.length);
                System.arraycopy(oarray, 0, mArray, 0, oarray.length);
            }
        }
        if (index < osize) {
            System.arraycopy(mHashes, index, mHashes, index + 1, osize - index);
            System.arraycopy(mArray, index << 1, mArray, (index + 1) << 1, (mSize - index) << 1);
        }
        if (osize != mSize || index >= mHashes.length) {
            throw new ConcurrentModificationException();
        }
        mHashes[index] = hash;
        mArray[index << 1] = key;
        mArray[(index << 1) + 1] = value;
        mSize++;
        return null;
    }

    public void putAll(ArrayMap<? extends K, ? extends V> array) {
        final int n = array.mSize;
        ensureCapacity(mSize + n);
        if (mSize == 0) {
            if (n > 0) {
                System.arraycopy(array.mHashes, 0, mHashes, 0, n);
                System.arraycopy(array.mArray, 0, mArray, 0, n << 1);
                mSize = n;
            }
        } else {
            for (int i = 0; i < n; i++) {
                put(array.keyAt(i), array.valueAt(i));
            }
        }
    }

    @Override
    public V remove(Object key) {
        final int index = indexOfKey(key);
        if (index >= 0) {
            return removeAt(index);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public V removeAt(int index) {
        if (index < 0 || index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        final Object old = mArray[(index << 1) + 1];
        final int osize = mSize;
        if (osize <= 1) {
            mHashes = EMPTY_INTS;
            mArray = EMPTY_OBJECTS;
            mSize = 0;
        } else {
            final int nsize = osize - 1;
            if (mHashes.length > (BASE_SIZE * 2) && mSize < mHashes.length / 3) {
                final int n = osize > (BASE_SIZE * 2) ? (osize + (osize >> 1)) : (BASE_SIZE * 2);
                final int[] ohashes = mHashes;
                final Object[] oarray = mArray;
                allocArrays(n);
                if (index > 0) {
                    System.arraycopy(ohashes, 0, mHashes, 0, index);
                    System.arraycopy(oarray, 0, mArray, 0, index << 1);
                }
                if (index < nsize) {
                    System.arraycopy(ohashes, index + 1, mHashes, index, nsize - index);
                    System.arraycopy(
                            oarray, (index + 1) << 1, mArray, index << 1, (nsize - index) << 1);
                }
            } else {
                if (index < nsize) {
                    System.arraycopy(mHashes, index + 1, mHashes, index, nsize - index);
                    System.arraycopy(
                            mArray, (index + 1) << 1, mArray, index << 1, (nsize - index) << 1);
                }
                mArray[nsize << 1] = null;
                mArray[(nsize << 1) + 1] = null;
            }
            if (osize != mSize) {
                throw new ConcurrentModificationException();
            }
            mSize = nsize;
        }
        return (V) old;
    }

    @Override
    public int size() {
        return mSize;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Map)) {
            return false;
        }
        Map<?, ?> map = (Map<?, ?>) object;
        if (size() != map.size()) {
            return false;
        }
        try {
            for (int i = 0; i < mSize; i++) {
                K key = keyAt(i);
                V mine = valueAt(i);
                Object theirs = map.get(key);
                if (mine == null) {
                    if (theirs != null || !map.containsKey(key)) {
                        return false;
                    }
                } else if (!mine.equals(theirs)) {
                    return false;
                }
            }
        } catch (NullPointerException | ClassCastException ignored) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int[] hashes = mHashes;
        final Object[] array = mArray;
        int result = 0;
        for (int i = 0, v = 1, s = mSize; i < s; i++, v += 2) {
            Object value = array[v];
            result += hashes[i] ^ (value == null ? 0 : value.hashCode());
        }
        return result;
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "{}";
        }
        StringBuilder buffer = new StringBuilder(mSize * 28);
        buffer.append('{');
        for (int i = 0; i < mSize; i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            Object key = keyAt(i);
            buffer.append(key == this ? "(this Map)" : key);
            buffer.append('=');
            Object value = valueAt(i);
            buffer.append(value == this ? "(this Map)" : value);
        }
        buffer.append('}');
        return buffer.toString();
    }

    public boolean containsAll(Collection<?> collection) {
        for (Object item : collection) {
            if (!containsKey(item)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        final int size = mSize;
        for (int i = 0; i < size; i++) {
            if (size != mSize) {
                throw new ConcurrentModificationException();
            }
            action.accept(keyAt(i), valueAt(i));
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        ensureCapacity(mSize + map.size());
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public boolean removeAll(Collection<?> collection) {
        int oldSize = mSize;
        for (Object item : collection) {
            remove(item);
        }
        return oldSize != mSize;
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        final int size = mSize;
        for (int i = 0; i < size; i++) {
            int valIndex = (i << 1) + 1;
            @SuppressWarnings("unchecked")
            V newVal = function.apply(keyAt(i), (V) mArray[valIndex]);
            if (size != mSize) {
                throw new ConcurrentModificationException();
            }
            mArray[valIndex] = newVal;
        }
    }

    public boolean retainAll(Collection<?> collection) {
        int oldSize = mSize;
        for (int i = mSize - 1; i >= 0; i--) {
            if (!collection.contains(mArray[i << 1])) {
                removeAt(i);
            }
        }
        return oldSize != mSize;
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        if (mEntrySet == null) {
            mEntrySet = new EntrySet();
        }
        return mEntrySet;
    }

    @Override
    public Set<K> keySet() {
        if (mKeySet == null) {
            mKeySet = new KeySet();
        }
        return mKeySet;
    }

    @Override
    public Collection<V> values() {
        if (mValues == null) {
            mValues = new ValueCollection();
        }
        return mValues;
    }

    private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new MapIterator();
        }

        @Override
        public int size() {
            return mSize;
        }

        @Override
        public void clear() {
            ArrayMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            int index = indexOfKey(e.getKey());
            return index >= 0 && Objects.equals(valueAt(index), e.getValue());
        }
    }

    private final class KeySet implements Set<K> {
        @Override
        public boolean add(K object) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends K> collection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            ArrayMap.this.clear();
        }

        @Override
        public boolean contains(Object object) {
            return containsKey(object);
        }

        @Override
        public boolean containsAll(Collection<?> collection) {
            return ArrayMap.this.containsAll(collection);
        }

        @Override
        public boolean isEmpty() {
            return ArrayMap.this.isEmpty();
        }

        @Override
        public Iterator<K> iterator() {
            return new IndexIterator<K>(0);
        }

        @Override
        public boolean remove(Object object) {
            int index = indexOfKey(object);
            if (index >= 0) {
                removeAt(index);
                return true;
            }
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> collection) {
            return ArrayMap.this.removeAll(collection);
        }

        @Override
        public boolean retainAll(Collection<?> collection) {
            return ArrayMap.this.retainAll(collection);
        }

        @Override
        public int size() {
            return mSize;
        }

        @Override
        public Object[] toArray() {
            Object[] result = new Object[mSize];
            for (int i = 0; i < mSize; i++) {
                result[i] = mArray[i << 1];
            }
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] array) {
            if (array.length < mSize) {
                array = (T[]) Array.newInstance(array.getClass().getComponentType(), mSize);
            }
            for (int i = 0; i < mSize; i++) {
                array[i] = (T) mArray[i << 1];
            }
            if (array.length > mSize) {
                array[mSize] = null;
            }
            return array;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Set)) {
                return false;
            }
            Set<?> s = (Set<?>) object;
            try {
                return size() == s.size() && containsAll(s);
            } catch (NullPointerException | ClassCastException ignored) {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int result = 0;
            for (int i = mSize - 1; i >= 0; i--) {
                Object obj = mArray[i << 1];
                result += obj == null ? 0 : obj.hashCode();
            }
            return result;
        }
    }

    private final class ValueCollection implements Collection<V> {
        @Override
        public boolean add(V object) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends V> collection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            ArrayMap.this.clear();
        }

        @Override
        public boolean contains(Object object) {
            return containsValue(object);
        }

        @Override
        public boolean containsAll(Collection<?> collection) {
            for (Object o : collection) {
                if (!contains(o)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean isEmpty() {
            return ArrayMap.this.isEmpty();
        }

        @Override
        public Iterator<V> iterator() {
            return new IndexIterator<V>(1);
        }

        @Override
        public boolean remove(Object object) {
            int index = indexOfValue(object);
            if (index >= 0) {
                removeAt(index);
                return true;
            }
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> collection) {
            int oldSize = mSize;
            for (int i = mSize - 1; i >= 0; i--) {
                if (collection.contains(valueAt(i))) {
                    removeAt(i);
                }
            }
            return oldSize != mSize;
        }

        @Override
        public boolean retainAll(Collection<?> collection) {
            int oldSize = mSize;
            for (int i = mSize - 1; i >= 0; i--) {
                if (!collection.contains(valueAt(i))) {
                    removeAt(i);
                }
            }
            return oldSize != mSize;
        }

        @Override
        public int size() {
            return mSize;
        }

        @Override
        public Object[] toArray() {
            Object[] result = new Object[mSize];
            for (int i = 0; i < mSize; i++) {
                result[i] = mArray[(i << 1) + 1];
            }
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] array) {
            if (array.length < mSize) {
                array = (T[]) Array.newInstance(array.getClass().getComponentType(), mSize);
            }
            for (int i = 0; i < mSize; i++) {
                array[i] = (T) mArray[(i << 1) + 1];
            }
            if (array.length > mSize) {
                array[mSize] = null;
            }
            return array;
        }
    }

    private final class IndexIterator<T> implements Iterator<T> {
        private final int mOffset;
        private int mSizeSnapshot = mSize;
        private int mIndex;
        private boolean mCanRemove;

        IndexIterator(int offset) {
            mOffset = offset;
        }

        @Override
        public boolean hasNext() {
            return mIndex < mSizeSnapshot;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Object item = mArray[(mIndex << 1) + mOffset];
            mIndex++;
            mCanRemove = true;
            return (T) item;
        }

        @Override
        public void remove() {
            if (!mCanRemove) {
                throw new IllegalStateException();
            }
            mIndex--;
            mSizeSnapshot--;
            mCanRemove = false;
            removeAt(mIndex);
        }
    }

    private final class MapIterator implements Iterator<Map.Entry<K, V>>, Map.Entry<K, V> {
        private int mEnd = mSize - 1;
        private int mIndex = -1;
        private boolean mEntryValid;

        @Override
        public boolean hasNext() {
            return mIndex < mEnd;
        }

        @Override
        public Map.Entry<K, V> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            mIndex++;
            mEntryValid = true;
            return this;
        }

        @Override
        public void remove() {
            if (!mEntryValid) {
                throw new IllegalStateException();
            }
            removeAt(mIndex);
            mIndex--;
            mEnd--;
            mEntryValid = false;
        }

        @Override
        public K getKey() {
            if (!mEntryValid) {
                throw new IllegalStateException("This container does not support retaining Map.Entry objects");
            }
            return keyAt(mIndex);
        }

        @Override
        public V getValue() {
            if (!mEntryValid) {
                throw new IllegalStateException("This container does not support retaining Map.Entry objects");
            }
            return valueAt(mIndex);
        }

        @Override
        public V setValue(V object) {
            if (!mEntryValid) {
                throw new IllegalStateException("This container does not support retaining Map.Entry objects");
            }
            return setValueAt(mIndex, object);
        }

        @Override
        public boolean equals(Object o) {
            if (!mEntryValid) {
                throw new IllegalStateException("This container does not support retaining Map.Entry objects");
            }
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return Objects.equals(e.getKey(), keyAt(mIndex))
                    && Objects.equals(e.getValue(), valueAt(mIndex));
        }

        @Override
        public int hashCode() {
            if (!mEntryValid) {
                throw new IllegalStateException("This container does not support retaining Map.Entry objects");
            }
            K k = keyAt(mIndex);
            V v = valueAt(mIndex);
            return (k == null ? 0 : k.hashCode()) ^ (v == null ? 0 : v.hashCode());
        }

        @Override
        public String toString() {
            return getKey() + "=" + getValue();
        }
    }
}
