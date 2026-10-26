// SPDX-License-Identifier: GPL-3.0-only
import android.util.SparseLongArray;
import java.util.*;
public final class SparseLongSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  SparseLongArray array=new SparseLongArray(0);TreeMap<Integer,Long> expected=new TreeMap<Integer,Long>();Random random=new Random(89123);
  array.append(Integer.MIN_VALUE,Long.MIN_VALUE);array.append(Integer.MAX_VALUE,Long.MAX_VALUE);check(array.valueAt(0)==Long.MIN_VALUE&&array.get(Integer.MAX_VALUE)==Long.MAX_VALUE,"Full width keys and values");array.clear();
  for(int step=0;step<4000;step++){
   int key=random.nextInt(200)-100;long value=random.nextLong();
   switch(random.nextInt(5)){
    case 0:array.put(key,value);expected.put(key,value);break;
    case 1:array.append(key,value);expected.put(key,value);break;
    case 2:array.delete(key);expected.remove(key);break;
    case 3:if(array.size()>0){int at=random.nextInt(array.size());int count=random.nextInt(10);ArrayList<Integer> keys=new ArrayList<Integer>(expected.keySet());for(int i=at;i<Math.min(keys.size(),at+count);i++)expected.remove(keys.get(i));array.removeAtRange(at,count);}break;
    default:check(array.get(key,Long.MIN_VALUE)==(expected.containsKey(key)?expected.get(key):Long.MIN_VALUE),"Missing fallback");
   }
   check(array.size()==expected.size(),"Mutation size");int at=0;for(Map.Entry<Integer,Long> entry:expected.entrySet()){check(array.keyAt(at)==entry.getKey()&&array.valueAt(at)==entry.getValue()&&array.indexOfKey(entry.getKey())==at,"Sorted differential contents");at++;}
  }
  array.clear();array.put(2,7);array.put(4,7);check(array.indexOfValue(7)==0&&array.indexOfValue(8)==-1&&array.indexOfKey(3)==~1,"Search semantics");SparseLongArray copy=array.clone();copy.setValueAt(0,Long.MAX_VALUE);copy.delete(4);check(array.get(2)==7&&array.size()==2&&copy.get(2)==Long.MAX_VALUE,"Independent clone");array.removeAtRange(1,Integer.MAX_VALUE);check(array.size()==1&&array.toString().equals("{2=7}"),"Clamped range and rendering");
  for(int i=0;i<65536;i++)array.put(i,i);try{array.put(-1,0);throw new AssertionError("Entry limit");}catch(IllegalStateException expectedFailure){}array.put(0,Long.MIN_VALUE);check(array.get(0)==Long.MIN_VALUE,"Replacement at bound");
  System.out.println("SPARSE_LONG_OK");
 }
}
