// SPDX-License-Identifier: GPL-3.0-only
package android.util;
import java.util.Arrays;
/** Sorted integer-to-boolean map with bounded allocation and independent clones. */
public class SparseBooleanArray implements Cloneable {
 private static final int LIMIT=65536;
 private int[] keys;private boolean[] values;private int size;
 public SparseBooleanArray(){this(10);}public SparseBooleanArray(int capacity){if(capacity<0||capacity>LIMIT)throw new IllegalArgumentException("SparseBooleanArray capacity");keys=new int[capacity];values=new boolean[capacity];}
 public int size(){return size;}public int indexOfKey(int key){return Arrays.binarySearch(keys,0,size,key);}
 public boolean get(int key){return get(key,false);}public boolean get(int key,boolean fallback){int at=indexOfKey(key);return at<0?fallback:values[at];}
 private void grow(){if(size==LIMIT)throw new IllegalStateException("SparseBooleanArray entry limit");if(size==keys.length){int capacity=Math.min(LIMIT,Math.max(4,size*2));int[] nextKeys=Arrays.copyOf(keys,capacity);boolean[] nextValues=Arrays.copyOf(values,capacity);keys=nextKeys;values=nextValues;}}
 public void put(int key,boolean value){int at=indexOfKey(key);if(at>=0){values[at]=value;return;}at=~at;grow();System.arraycopy(keys,at,keys,at+1,size-at);System.arraycopy(values,at,values,at+1,size-at);keys[at]=key;values[at]=value;size++;}
 public void append(int key,boolean value){if(size>0&&key<=keys[size-1]){put(key,value);return;}grow();keys[size]=key;values[size++]=value;}
 public void delete(int key){int at=indexOfKey(key);if(at>=0)removeAt(at);}
 private void index(int at){if(at<0||at>=size)throw new ArrayIndexOutOfBoundsException(at);}
 public void removeAt(int at){index(at);System.arraycopy(keys,at+1,keys,at,size-at-1);System.arraycopy(values,at+1,values,at,size-at-1);size--;}
 public int keyAt(int at){index(at);return keys[at];}public boolean valueAt(int at){index(at);return values[at];}public void setValueAt(int at,boolean value){index(at);values[at]=value;}
 public int indexOfValue(boolean value){for(int i=0;i<size;i++)if(values[i]==value)return i;return -1;}
 public void setKeyAt(int at,int key){index(at);keys[at]=key;}
 @Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof SparseBooleanArray))return false;SparseBooleanArray array=(SparseBooleanArray)other;if(size!=array.size)return false;for(int i=0;i<size;i++)if(keys[i]!=array.keys[i]||values[i]!=array.values[i])return false;return true;}
 @Override public int hashCode(){int hash=size;for(int i=0;i<size;i++)hash=(31*hash+keys[i])|(values[i]?1:0);return hash;}
 public void clear(){size=0;}
 @Override public SparseBooleanArray clone(){try{SparseBooleanArray result=(SparseBooleanArray)super.clone();result.keys=keys.clone();result.values=values.clone();return result;}catch(CloneNotSupportedException error){throw new AssertionError(error);}}
 @Override public String toString(){StringBuilder out=new StringBuilder("{");for(int i=0;i<size;i++){if(i>0)out.append(", ");out.append(keys[i]).append('=').append(values[i]);}return out.append('}').toString();}
}
