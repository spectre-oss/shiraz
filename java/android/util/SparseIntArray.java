// SPDX-License-Identifier: GPL-3.0-only
package android.util;
import java.util.Arrays;
/** Sorted primitive integer map with bounded allocation and independent clones. */
public class SparseIntArray implements Cloneable {
 private static final int LIMIT=65536;
 private int[] keys,values;private int size;
 public SparseIntArray(){this(10);}public SparseIntArray(int capacity){if(capacity<0||capacity>LIMIT)throw new IllegalArgumentException("SparseIntArray capacity");keys=new int[capacity];values=new int[capacity];}
 public int size(){return size;}public int indexOfKey(int key){return Arrays.binarySearch(keys,0,size,key);}
 public int get(int key){return get(key,0);}public int get(int key,int fallback){int at=indexOfKey(key);return at<0?fallback:values[at];}
 private void grow(){if(size==LIMIT)throw new IllegalStateException("SparseIntArray entry limit");if(size==keys.length){int capacity=Math.min(LIMIT,Math.max(4,size*2));int[] nextKeys=Arrays.copyOf(keys,capacity),nextValues=Arrays.copyOf(values,capacity);keys=nextKeys;values=nextValues;}}
 public void put(int key,int value){int at=indexOfKey(key);if(at>=0){values[at]=value;return;}at=~at;grow();System.arraycopy(keys,at,keys,at+1,size-at);System.arraycopy(values,at,values,at+1,size-at);keys[at]=key;values[at]=value;size++;}
 public void append(int key,int value){if(size>0&&key<=keys[size-1]){put(key,value);return;}grow();keys[size]=key;values[size++]=value;}
 public void delete(int key){int at=indexOfKey(key);if(at>=0)removeAt(at);}
 private void index(int at){if(at<0||at>=size)throw new ArrayIndexOutOfBoundsException(at);}
 public void removeAt(int at){index(at);System.arraycopy(keys,at+1,keys,at,size-at-1);System.arraycopy(values,at+1,values,at,size-at-1);size--;}
 public int keyAt(int at){index(at);return keys[at];}public int valueAt(int at){index(at);return values[at];}public void setValueAt(int at,int value){index(at);values[at]=value;}
 public int indexOfValue(int value){for(int i=0;i<size;i++)if(values[i]==value)return i;return -1;}
 public void clear(){size=0;}
 public int[] copyKeys(){return size==0?null:Arrays.copyOf(keys,size);}
 @Override public SparseIntArray clone(){try{SparseIntArray result=(SparseIntArray)super.clone();result.keys=keys.clone();result.values=values.clone();return result;}catch(CloneNotSupportedException error){throw new AssertionError(error);}}
 @Override public String toString(){StringBuilder out=new StringBuilder("{");for(int i=0;i<size;i++){if(i>0)out.append(", ");out.append(keys[i]).append('=').append(values[i]);}return out.append('}').toString();}
}
