// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.util.SparseIntArray;
import java.util.*;
public final class SparseIntFixture {
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void check(){SparseIntArray actual=new SparseIntArray(0);TreeMap<Integer,Integer> expected=new TreeMap<Integer,Integer>();Random random=new Random(77);
  for(int step=0;step<2000;step++){int key=random.nextInt(100)-50,value=random.nextInt();switch(random.nextInt(5)){case 0:actual.delete(key);expected.remove(key);break;case 1:actual.append(key,value);expected.put(key,value);break;case 2:if(actual.size()>0){int at=random.nextInt(actual.size());expected.remove(actual.keyAt(at));actual.removeAt(at);}break;case 3:if(actual.size()>0){int at=random.nextInt(actual.size());expected.put(actual.keyAt(at),value);actual.setValueAt(at,value);}break;default:actual.put(key,value);expected.put(key,value);}
   check(actual.size()==expected.size(),"Size matches reference");int at=0;for(Map.Entry<Integer,Integer> entry:expected.entrySet()){check(actual.keyAt(at)==entry.getKey()&&actual.valueAt(at)==entry.getValue()&&actual.indexOfKey(entry.getKey())==at,"Sorted map matches reference");at++;}check(actual.get(key,123)==(expected.containsKey(key)?expected.get(key):123),"Fallback lookup");}
  actual.clear();actual.append(Integer.MAX_VALUE,7);actual.append(Integer.MIN_VALUE,7);actual.put(0,-3);check(actual.indexOfKey(-1)==-2&&actual.indexOfValue(7)==0,"Insertion point and first matching value");
  SparseIntArray clone=actual.clone();clone.delete(0);clone.put(Integer.MIN_VALUE,9);check(actual.size()==3&&actual.get(Integer.MIN_VALUE)==7,"Clone ownership");check(actual.toString().equals("{-2147483648=7, 0=-3, 2147483647=7}"),"Sorted string");
  try{actual.valueAt(actual.size());throw new AssertionError("Index accepted");}catch(ArrayIndexOutOfBoundsException good){}
  actual.clear();check(actual.size()==0&&actual.get(42)==0&&actual.toString().equals("{}"),"Clear and zero default");actual.append(4,8);check(actual.get(4)==8,"Reuse cleared storage");
  System.out.println("SPARSE_INT_REFERENCE_OK");
 }
}
