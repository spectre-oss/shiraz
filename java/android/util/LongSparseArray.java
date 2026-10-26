// SPDX-License-Identifier: GPL-3.0-only
package android.util;
import java.util.Arrays;
/** Sorted long-key map with shallow value ownership and a bounded entry count. */
public class LongSparseArray<E> implements Cloneable {
    private long[] keys;private Object[] values;private int size;
    public LongSparseArray(){this(10);}
    public LongSparseArray(int capacity){if(capacity<0||capacity>65536)throw new IllegalArgumentException("sparse array capacity");keys=new long[capacity];values=new Object[capacity];}
    public int indexOfKey(long key){return Arrays.binarySearch(keys,0,size,key);}
    public boolean contains(long key){return indexOfKey(key)>=0;}
    public E get(long key){return get(key,null);}
    @SuppressWarnings("unchecked") public E get(long key,E fallback){int at=indexOfKey(key);return at<0?fallback:(E)values[at];}
    public void put(long key,E value){int at=indexOfKey(key);if(at>=0){values[at]=value;return;}at=~at;if(size==65536)throw new IllegalStateException("sparse array limit");if(size==keys.length){int n=Math.min(65536,Math.max(4,size*2));keys=Arrays.copyOf(keys,n);values=Arrays.copyOf(values,n);}System.arraycopy(keys,at,keys,at+1,size-at);System.arraycopy(values,at,values,at+1,size-at);keys[at]=key;values[at]=value;size++;}
    public void append(long key,E value){put(key,value);}
    public void set(long key,E value){put(key,value);}
    public int size(){return size;}
    private void index(int at){if(at<0||at>=size)throw new ArrayIndexOutOfBoundsException(at);}
    public long keyAt(int at){index(at);return keys[at];}
    @SuppressWarnings("unchecked") public E valueAt(int at){index(at);return (E)values[at];}
    public void setValueAt(int at,E value){index(at);values[at]=value;}
    public int indexOfValue(E value){for(int i=0;i<size;i++)if(values[i]==value)return i;return -1;}
    public void delete(long key){remove(key);}
    public void remove(long key){int at=indexOfKey(key);if(at>=0)removeAt(at);}
    public void removeAt(int at){index(at);System.arraycopy(keys,at+1,keys,at,size-at-1);System.arraycopy(values,at+1,values,at,size-at-1);values[--size]=null;}
    public void removeAtRange(int at,int count){if(at<0||at>size||count<0)throw new IndexOutOfBoundsException();int end=(int)Math.min(size,(long)at+count),n=end-at;System.arraycopy(keys,end,keys,at,size-end);System.arraycopy(values,end,values,at,size-end);Arrays.fill(values,size-n,size,null);size-=n;}
    public void clear(){Arrays.fill(values,0,size,null);size=0;}
    public LongSparseArray<E> clone(){LongSparseArray<E> result=new LongSparseArray<E>(size);result.keys=Arrays.copyOf(keys,size);result.values=Arrays.copyOf(values,size);result.size=size;return result;}
    public String toString(){StringBuilder out=new StringBuilder("{");for(int i=0;i<size;i++){if(i>0)out.append(", ");out.append(keys[i]).append('=').append(values[i]==this?"(this Map)":values[i]);}return out.append('}').toString();}
}
