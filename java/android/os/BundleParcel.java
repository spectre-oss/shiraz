// SPDX-License-Identifier: GPL-3.0-only
package android.os;
import java.util.*;
/** Bounded eager Bundle encoding for Shiraz's Java Parcel peers. */
final class BundleParcel {
 private static final class Budget {int depth,items;final IdentityHashMap<Object,Boolean> active=new IdentityHashMap<Object,Boolean>();}
 private static final ThreadLocal<Budget> WRITING=new ThreadLocal<Budget>(),READING=new ThreadLocal<Budget>();
 private static Budget enter(ThreadLocal<Budget> local){Budget b=local.get();if(b==null){b=new Budget();local.set(b);}if(++b.depth>64){b.depth--;throw new IllegalArgumentException("Bundle parcel nesting limit");}return b;}
 private static void leave(ThreadLocal<Budget> local,Budget b){if(--b.depth==0)local.remove();}
 private static void item(Budget b){if(++b.items>65536)throw new IllegalArgumentException("Bundle parcel item limit");}
 static void write(Parcel out,Bundle bundle,int flags){Budget b=enter(WRITING);boolean added=false;
  try{if(b.active.put(bundle,Boolean.TRUE)!=null)throw new IllegalArgumentException("Cyclic Bundle parcel");added=true;
   if(bundle.size()>65536)throw new IllegalArgumentException("Bundle entry limit");out.writeInt(bundle.size());
   for(Map.Entry<String,Object> entry:bundle.values.entrySet()){item(b);out.writeString(entry.getKey());writeValue(out,entry.getValue(),flags,b);}
  }finally{if(added)b.active.remove(bundle);leave(WRITING,b);}
 }
 static Bundle read(Parcel in,ClassLoader loader){Budget b=enter(READING);try{
  int count=count(in);Bundle bundle=new Bundle(loader);for(int i=0;i<count;i++){item(b);String key=in.readString();bundle.values.put(key,readValue(in,loader,b));}return bundle;
 }finally{leave(READING,b);}}
 private static int count(Parcel in){int count=in.readInt();if(count<0||count>65536)throw new IllegalArgumentException("Bundle collection limit");return count;}
 private static void writeValue(Parcel p,Object v,int flags,Budget b){
  item(b);
  if(v==null){p.writeInt(0);return;}
  if(v instanceof String){p.writeInt(1);p.writeString((String)v);}
  else if(v instanceof Integer){p.writeInt(2);p.writeInt((Integer)v);}
  else if(v instanceof Long){p.writeInt(3);p.writeLong((Long)v);}
  else if(v instanceof Boolean){p.writeInt(4);p.writeInt((Boolean)v?1:0);}
  else if(v instanceof Float){p.writeInt(5);p.writeFloat((Float)v);}
  else if(v instanceof Double){p.writeInt(6);p.writeDouble((Double)v);}
  else if(v instanceof Byte){p.writeInt(7);p.writeInt((Byte)v);}
  else if(v instanceof Short){p.writeInt(8);p.writeInt((Short)v);}
  else if(v instanceof Character){p.writeInt(9);p.writeInt((Character)v);}
  else if(v instanceof Bundle){p.writeInt(10);write(p,(Bundle)v,flags);}
  else if(v instanceof IBinder){p.writeInt(11);p.writeStrongBinder((IBinder)v);}
  else if(v instanceof byte[]){p.writeInt(12);p.writeByteArray((byte[])v);}
  else if(v instanceof int[]){p.writeInt(13);p.writeIntArray((int[])v);}
  else if(v instanceof long[]){p.writeInt(14);p.writeLongArray((long[])v);}
  else if(v instanceof String[]){p.writeInt(15);p.writeStringArray((String[])v);}
  else if(v instanceof boolean[]){p.writeInt(16);p.writeBooleanArray((boolean[])v);}
  else if(v instanceof float[]){p.writeInt(17);p.writeFloatArray((float[])v);}
  else if(v instanceof double[]){p.writeInt(18);p.writeDoubleArray((double[])v);}
  else if(v instanceof Parcelable){p.writeInt(19);p.writeParcelable((Parcelable)v,flags);}
  else if(v instanceof CharSequence){p.writeInt(20);p.writeString(v.toString());}
  else if(v instanceof ArrayList||v instanceof Parcelable[]||v instanceof CharSequence[]){
   if(b.active.put(v,Boolean.TRUE)!=null)throw new IllegalArgumentException("Cyclic Bundle collection");
   if(++b.depth>64){b.depth--;b.active.remove(v);throw new IllegalArgumentException("Bundle collection depth");}
   try{p.writeInt(v instanceof ArrayList?21:v instanceof Parcelable[]?22:23);int count=v instanceof ArrayList?((ArrayList<?>)v).size():((Object[])v).length;
    if(count>65536)throw new IllegalArgumentException("Bundle collection limit");p.writeInt(count);
    for(int i=0;i<count;i++)writeValue(p,v instanceof ArrayList?((ArrayList<?>)v).get(i):((Object[])v)[i],flags,b);
   }finally{b.depth--;b.active.remove(v);}
  }else throw new UnsupportedOperationException("Bundle value encoding: "+v.getClass().getName());
 }
 private static Object readValue(Parcel p,ClassLoader loader,Budget b){item(b);int type=p.readInt();switch(type){
  case 0:return null;case 1:return p.readString();case 2:return p.readInt();case 3:return p.readLong();case 4:return p.readInt()!=0;
  case 5:return p.readFloat();case 6:return p.readDouble();case 7:return (byte)p.readInt();case 8:return (short)p.readInt();case 9:return (char)p.readInt();
  case 10:return read(p,loader);case 11:return p.readStrongBinder();case 12:return p.createByteArray();case 13:return p.createIntArray();case 14:return p.createLongArray();
  case 15:return p.createStringArray();case 16:return p.createBooleanArray();case 17:return p.createFloatArray();case 18:return p.createDoubleArray();case 19:return p.readParcelable(loader);case 20:return p.readString();
  case 21:case 22:case 23:{if(++b.depth>64){b.depth--;throw new IllegalArgumentException("Bundle collection depth");}
   try{int count=count(p);if(type==21){ArrayList<Object> result=new ArrayList<Object>(count);for(int i=0;i<count;i++)result.add(readValue(p,loader,b));return result;}
    Object[] result=type==22?new Parcelable[count]:new CharSequence[count];for(int i=0;i<count;i++)result[i]=readValue(p,loader,b);return result;
   }finally{b.depth--;}}
  default:throw new IllegalArgumentException("Unknown Bundle value type");
 }}
}
