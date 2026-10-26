// SPDX-License-Identifier: GPL-3.0-only
package android.os;
import java.util.*;
/** Persistable value subset in memory. Parcel/XML/disk transport is not implemented. */
public final class PersistableBundle extends BaseBundle implements Parcelable,Cloneable {
 public static final PersistableBundle EMPTY=new PersistableBundle(true);
 public PersistableBundle(){super();}public PersistableBundle(int capacity){super(capacity);}private PersistableBundle(boolean immutable){super(immutable);}
 public PersistableBundle(PersistableBundle other){super(other);}
 public PersistableBundle(Bundle other){super();values.putAll(convert(other,new IdentityHashMap<BaseBundle,Boolean>(),0,new int[]{0}).values);}
 private static PersistableBundle convert(BaseBundle source,IdentityHashMap<BaseBundle,Boolean> active,int depth,int[] budget){
  if(source==null)throw new NullPointerException("bundle");if(depth>64||active.put(source,Boolean.TRUE)!=null)throw new IllegalArgumentException("Persistable bundle cycle/depth");
  PersistableBundle out=new PersistableBundle();try{for(Map.Entry<String,Object> entry:source.values.entrySet()){
   if(++budget[0]>65536)throw new IllegalArgumentException("Persistable bundle size");Object value=entry.getValue();
   if(value instanceof Bundle||value instanceof PersistableBundle)value=convert((BaseBundle)value,active,depth+1,budget);
   else if(value instanceof boolean[])value=((boolean[])value).clone();else if(value instanceof int[])value=((int[])value).clone();else if(value instanceof long[])value=((long[])value).clone();else if(value instanceof double[])value=((double[])value).clone();else if(value instanceof String[])value=((String[])value).clone();
   else if(value!=null&&!(value instanceof String)&&!(value instanceof Boolean)&&!(value instanceof Integer)&&!(value instanceof Long)&&!(value instanceof Double))throw new IllegalArgumentException("Non-persistable bundle value");
   out.values.put(entry.getKey(),value);
  }}finally{active.remove(source);}return out;
 }
 public PersistableBundle deepCopy(){return convert(this,new IdentityHashMap<BaseBundle,Boolean>(),0,new int[]{0});}
 @Override public Object clone(){return new PersistableBundle(this);}
 public void putAll(PersistableBundle other){values.putAll(other.values);}
 public void putPersistableBundle(String key,PersistableBundle value){values.put(key,value);}
 public PersistableBundle getPersistableBundle(String key){return value(key,PersistableBundle.class,null);}
 // BaseBundle exposes broader setters in Shiraz than Android's persistable public API.
 private void invalid(){throw new IllegalArgumentException("Non-persistable value type");}
 @Override public void putByte(String key,byte value){invalid();}@Override public void putShort(String key,short value){invalid();}@Override public void putChar(String key,char value){invalid();}@Override public void putFloat(String key,float value){invalid();}
 @Override public void putByteArray(String key,byte[] value){invalid();}@Override public void putShortArray(String key,short[] value){invalid();}@Override public void putCharArray(String key,char[] value){invalid();}@Override public void putFloatArray(String key,float[] value){invalid();}
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel destination,int flags){throw new UnsupportedOperationException("PersistableBundle parcel transport");}
 public static final Parcelable.Creator<PersistableBundle> CREATOR=new Parcelable.Creator<PersistableBundle>(){
  public PersistableBundle createFromParcel(Parcel source){throw new UnsupportedOperationException("PersistableBundle parcel transport");}
  public PersistableBundle[] newArray(int size){return new PersistableBundle[size];}
 };
}
