// SPDX-License-Identifier: GPL-3.0-only
package android.util;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Memory-efficient generic {@link Set} storing sorted element hashes in an {@code int[]} and
 * values in a parallel {@code Object[]}.
 */
public final class ArraySet<E> implements Collection<E>, Set<E> {
    private static final int BASE_SIZE = 4;
    private static final int[] EMPTY_INTS = new int[0];
    private static final Object[] EMPTY_OBJECTS = new Object[0];

    private int[] mHashes;
    private Object[] mArray;
    private int mSize;

    public ArraySet() {
        mHashes = EMPTY_INTS;
        mArray = EMPTY_OBJECTS;
        mSize = 0;
    }

    public ArraySet(int capacity) {
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

    public ArraySet(ArraySet<E> set) {
        this();
        if (set != null) {
            addAll(set);
        }
    }

    public ArraySet(Collection<? extends E> set) {
        this();
        if (set != null) {
            addAll(set);
        }
    }

    public ArraySet(E[] array) {
        this();
        if (array != null) {
            ensureCapacity(array.length);
            for (E value : array) {
                add(value);
            }
        }
    }

    private void allocArrays(int size) {
        mHashes = new int[size];
        mArray = new Object[size];
    }

    private int indexOf(Object key, int hash) {
        final int n = mSize;
        if (n == 0) {
            return ~0;
        }
        int index = Arrays.binarySearch(mHashes, 0, n, hash);
        if (index < 0) {
            return index;
        }
        if (Objects.equals(key, mArray[index])) {
            return index;
        }
        int end;
        for (end = index + 1; end < n && mHashes[end] == hash; end++) {
            if (Objects.equals(key, mArray[end])) {
                return end;
            }
        }
        for (int i = index - 1; i >= 0 && mHashes[i] == hash; i--) {
            if (Objects.equals(key, mArray[i])) {
                return i;
            }
        }
        return ~end;
    }

    private int indexOfNull() {
        final int n = mSize;
        if (n == 0) {
            return ~0;
        }
        int index = Arrays.binarySearch(mHashes, 0, n, 0);
        if (index < 0) {
            return index;
        }
        if (mArray[index] == null) {
            return index;
        }
        int end;
        for (end = index + 1; end < n && mHashes[end] == 0; end++) {
            if (mArray[end] == null) {
                return end;
            }
        }
        for (int i = index - 1; i >= 0 && mHashes[i] == 0; i--) {
            if (mArray[i] == null) {
                return i;
            }
        }
        return ~end;
    }

    @Override
    public void clear() {
        if (mSize != 0) {
            mHashes = EMPTY_INTS;
            mArray = EMPTY_OBJECTS;
            mSize = 0;
        }
    }

    public void ensureCapacity(int minimumCapacity) {
        final int osize = mSize;
        if (mHashes.length < minimumCapacity) {
            final int[] ohashes = mHashes;
            final Object[] oarray = mArray;
            allocArrays(minimumCapacity);
            if (mSize > 0) {
                System.arraycopy(ohashes, 0, mHashes, 0, mSize);
                System.arraycopy(oarray, 0, mArray, 0, mSize);
            }
        }
        if (mSize != osize) {
            throw new ConcurrentModificationException();
        }
    }

    @Override
    public boolean contains(Object key) {
        return indexOf(key) >= 0;
    }

    public int indexOf(Object key) {
        return key == null ? indexOfNull() : indexOf(key, key.hashCode());
    }

    @SuppressWarnings("unchecked")
    public E valueAt(int index) {
        if (index < 0 || index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return (E) mArray[index];
    }

    @Override
    public boolean isEmpty() {
        return mSize <= 0;
    }

    @Override
    public boolean add(E value) {
        final int osize = mSize;
        final int hash;
        int index;
        if (value == null) {
            hash = 0;
            index = indexOfNull();
        } else {
            hash = value.hashCode();
            index = indexOf(value, hash);
        }
        if (index >= 0) {
            return false;
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
            System.arraycopy(mArray, index, mArray, index + 1, osize - index);
        }
        if (osize != mSize || index >= mHashes.length) {
            throw new ConcurrentModificationException();
        }
        mHashes[index] = hash;
        mArray[index] = value;
        mSize++;
        return true;
    }

    public void addAll(ArraySet<? extends E> array) {
        final int n = array.mSize;
        ensureCapacity(mSize + n);
        if (mSize == 0) {
            if (n > 0) {
                System.arraycopy(array.mHashes, 0, mHashes, 0, n);
                System.arraycopy(array.mArray, 0, mArray, 0, n);
                mSize = n;
            }
        } else {
            for (int i = 0; i < n; i++) {
                add(array.valueAt(i));
            }
        }
    }

    @Override
    public boolean remove(Object object) {
        final int index = indexOf(object);
        if (index >= 0) {
            removeAt(index);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public E removeAt(int index) {
        if (index < 0 || index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        final int osize = mSize;
        final Object old = mArray[index];
        if (osize <= 1) {
            clear();
        } else {
            final int nsize = osize - 1;
            if (mHashes.length > (BASE_SIZE * 2) && mSize < mHashes.length / 3) {
                final int n = osize > (BASE_SIZE * 2) ? (osize + (osize >> 1)) : (BASE_SIZE * 2);
                final int[] ohashes = mHashes;
                final Object[] oarray = mArray;
                allocArrays(n);
                if (index > 0) {
                    System.arraycopy(ohashes, 0, mHashes, 0, index);
                    System.arraycopy(oarray, 0, mArray, 0, index);
                }
                if (index < nsize) {
                    System.arraycopy(ohashes, index + 1, mHashes, index, nsize - index);
                    System.arraycopy(oarray, index + 1, mArray, index, nsize - index);
                }
            } else {
                if (index < nsize) {
                    System.arraycopy(mHashes, index + 1, mHashes, index, nsize - index);
                    System.arraycopy(mArray, index + 1, mArray, index, nsize - index);
                }
                mArray[nsize] = null;
            }
            if (osize != mSize) {
                throw new ConcurrentModificationException();
            }
            mSize = nsize;
        }
        return (E) old;
    }

    public boolean removeAll(ArraySet<? extends E> array) {
        final int n = array.mSize;
        final int originalSize = mSize;
        for (int i = 0; i < n; i++) {
            remove(array.valueAt(i));
        }
        return originalSize != mSize;
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        boolean removed = false;
        for (int i = mSize - 1; i >= 0; i--) {
            if (filter.test(valueAt(i))) {
                removeAt(i);
                removed = true;
            }
        }
        return removed;
    }

    @Override
    public int size() {
        return mSize;
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        final int size = mSize;
        for (int i = 0; i < size; i++) {
            if (size != mSize) {
                throw new ConcurrentModificationException();
            }
            action.accept(valueAt(i));
        }
    }

    @Override
    public Object[] toArray() {
        Object[] result = new Object[mSize];
        System.arraycopy(mArray, 0, result, 0, mSize);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] array) {
        if (array.length < mSize) {
            array = (T[]) Array.newInstance(array.getClass().getComponentType(), mSize);
        }
        System.arraycopy(mArray, 0, array, 0, mSize);
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
        Set<?> set = (Set<?>) object;
        if (size() != set.size()) {
            return false;
        }
        try {
            for (int i = 0; i < mSize; i++) {
                if (!set.contains(valueAt(i))) {
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
        int result = 0;
        for (int i = 0, s = mSize; i < s; i++) {
            result += hashes[i];
        }
        return result;
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "{}";
        }
        StringBuilder buffer = new StringBuilder(mSize * 14);
        buffer.append('{');
        for (int i = 0; i < mSize; i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            Object value = valueAt(i);
            buffer.append(value == this ? "(this Set)" : value);
        }
        buffer.append('}');
        return buffer.toString();
    }

    @Override
    public Iterator<E> iterator() {
        return new SetIterator();
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
        for (Object item : collection) {
            if (!contains(item)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends E> collection) {
        ensureCapacity(mSize + collection.size());
        boolean added = false;
        for (E value : collection) {
            added |= add(value);
        }
        return added;
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
        boolean removed = false;
        for (Object value : collection) {
            removed |= remove(value);
        }
        return removed;
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
        boolean removed = false;
        for (int i = mSize - 1; i >= 0; i--) {
            if (!collection.contains(mArray[i])) {
                removeAt(i);
                removed = true;
            }
        }
        return removed;
    }

    private final class SetIterator implements Iterator<E> {
        private int mSizeSnapshot = mSize;
        private int mIndex;
        private boolean mCanRemove;

        @Override
        public boolean hasNext() {
            return mIndex < mSizeSnapshot;
        }

        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            E item = valueAt(mIndex);
            mIndex++;
            mCanRemove = true;
            return item;
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
}
