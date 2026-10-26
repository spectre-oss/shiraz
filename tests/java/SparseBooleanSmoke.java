// SPDX-License-Identifier: GPL-3.0-only
import android.util.SparseBooleanArray;
import java.util.*;
public final class SparseBooleanSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  SparseBooleanArray array=new SparseBooleanArray(0);TreeMap<Integer,Boolean> expected=new TreeMap<Integer,Boolean>();Random random=new Random(92275);
  for(int step=0;step<4000;step++){
   int key=random.nextInt(200)-100;boolean value=random.nextBoolean();
   switch(random.nextInt(5)){
    case 0:array.put(key,value);expected.put(key,value);break;
    case 1:array.append(key,value);expected.put(key,value);break;
    case 2:array.delete(key);expected.remove(key);break;
    case 3:if(array.size()>0){int at=random.nextInt(array.size());expected.remove(array.keyAt(at));array.removeAt(at);}break;
    default:check(array.get(key,true)==(expected.containsKey(key)?expected.get(key):true),"Missing fallback");
   }
   check(array.size()==expected.size(),"Mutation size");int at=0;for(Map.Entry<Integer,Boolean> entry:expected.entrySet()){check(array.keyAt(at)==entry.getKey()&&array.valueAt(at)==entry.getValue()&&array.indexOfKey(entry.getKey())==at,"Sorted differential contents");at++;}
  }
  array.clear();array.put(Integer.MIN_VALUE,false);array.put(Integer.MAX_VALUE,true);check(!array.get(0)&&array.indexOfValue(false)==0&&array.indexOfValue(true)==1,"Extremes and first matching value");SparseBooleanArray copy=array.clone();check(copy.equals(array)&&copy.hashCode()==array.hashCode()&&!copy.equals(null),"Structural equality");copy.setValueAt(0,true);copy.setKeyAt(0,-1);check(!array.get(Integer.MIN_VALUE)&&copy.get(-1)&&!copy.equals(array),"Independent clone mutations");array.clear();check(array.toString().equals("{}"),"Empty rendering");array.append(2,false);array.append(1,true);check(array.toString().equals("{1=true, 2=false}"),"Append fallback ordering");
  for(int i=0;i<65536;i++)array.put(i,false);try{array.put(-1,true);throw new AssertionError("Entry limit");}catch(IllegalStateException expectedFailure){}array.put(0,true);check(array.get(0),"Replacement at bound");
  System.out.println("SPARSE_BOOLEAN_OK");
 }
}
