// SPDX-License-Identifier: GPL-3.0-only
package android.util;
import java.util.Arrays;
/** Sorted integer-to-long map with bounded allocation and independent clones. */
public class SparseLongArray implements Cloneable {
 private static final int LIMIT=65536;
 private int[] keys;private long[] values;private int size;
 public SparseLongArray(){this(10);}public SparseLongArray(int capacity){if(capacity<0||capacity>LIMIT)throw new IllegalArgumentException("SparseLongArray capacity");keys=new int[capacity];values=new long[capacity];}
 public int size(){return size;}public int indexOfKey(int key){return Arrays.binarySearch(keys,0,size,key);}
 public long get(int key){return get(key,0);}public long get(int key,long fallback){int at=indexOfKey(key);return at<0?fallback:values[at];}
 private void grow(){if(size==LIMIT)throw new IllegalStateException("SparseLongArray entry limit");if(size==keys.length){int capacity=Math.min(LIMIT,Math.max(4,size*2));int[] nextKeys=Arrays.copyOf(keys,capacity);long[] nextValues=Arrays.copyOf(values,capacity);keys=nextKeys;values=nextValues;}}
 public void put(int key,long value){int at=indexOfKey(key);if(at>=0){values[at]=value;return;}at=~at;grow();System.arraycopy(keys,at,keys,at+1,size-at);System.arraycopy(values,at,values,at+1,size-at);keys[at]=key;values[at]=value;size++;}
 public void append(int key,long value){if(size>0&&key<=keys[size-1]){put(key,value);return;}grow();keys[size]=key;values[size++]=value;}
 public void delete(int key){int at=indexOfKey(key);if(at>=0)removeAt(at);}
 private void index(int at){if(at<0||at>=size)throw new ArrayIndexOutOfBoundsException(at);}
 public void removeAt(int at){index(at);System.arraycopy(keys,at+1,keys,at,size-at-1);System.arraycopy(values,at+1,values,at,size-at-1);size--;}
 public int keyAt(int at){index(at);return keys[at];}public long valueAt(int at){index(at);return values[at];}public void setValueAt(int at,long value){index(at);values[at]=value;}
 public int indexOfValue(long value){for(int i=0;i<size;i++)if(values[i]==value)return i;return -1;}
 public void clear(){size=0;}
 public void removeAtRange(int at,int count){if(at<0||at>size||count<0)throw new ArrayIndexOutOfBoundsException(at);int removed=Math.min(count,size-at);System.arraycopy(keys,at+removed,keys,at,size-at-removed);System.arraycopy(values,at+removed,values,at,size-at-removed);size-=removed;}
 @Override public SparseLongArray clone(){try{SparseLongArray result=(SparseLongArray)super.clone();result.keys=keys.clone();result.values=values.clone();return result;}catch(CloneNotSupportedException error){throw new AssertionError(error);}}
 @Override public String toString(){StringBuilder out=new StringBuilder("{");for(int i=0;i<size;i++){if(i>0)out.append(", ");out.append(keys[i]).append('=').append(values[i]);}return out.append('}').toString();}
}
