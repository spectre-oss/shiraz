// SPDX-License-Identifier: GPL-3.0-only
package android.os;
import java.util.ArrayList;
/** Bounded parcel data with owned local Binder/descriptor attachments. */
public final class Parcel {
 private static final int MAX=16*1024*1024;
 private static final ThreadLocal<ArrayList<Parcel>> POOL=new ThreadLocal<ArrayList<Parcel>>(){protected ArrayList<Parcel> initialValue(){return new ArrayList<Parcel>();}};
 private byte[] data=new byte[64];
 private int size,position;
 private java.util.TreeMap<Integer,Object> objects=new java.util.TreeMap<Integer,Object>();
 private void discardObjects(int start,int end){
  java.util.Iterator<java.util.Map.Entry<Integer,Object>> iterator=objects.entrySet().iterator();
  while(iterator.hasNext()){
   java.util.Map.Entry<Integer,Object> entry=iterator.next();
   if(entry.getKey()<end&&entry.getKey()+4>start){
    Object value=entry.getValue();iterator.remove();
    if(value instanceof ParcelFileDescriptor)try{((ParcelFileDescriptor)value).close();}catch(java.io.IOException ignored){}
   }
  }
 }

 private boolean recycled;
 private Parcel(){}
 public static Parcel obtain(){
  ArrayList<Parcel> pool=POOL.get();
  if(!pool.isEmpty()){Parcel parcel=pool.remove(pool.size()-1);parcel.recycled=false;parcel.size=0;parcel.position=0;return parcel;}
  return new Parcel();
 }
 public final void recycle(){
  if(recycled)return;
  discardObjects(0,MAX);recycled=true;size=0;position=0;
  ArrayList<Parcel> pool=POOL.get();
  if(pool.size()<32)pool.add(this);
 }
 private void live(){if(recycled)throw new IllegalStateException("Parcel is recycled");}
 private void ensure(int extra){
  live();
  int need=position+extra;
  if(need<0||need>MAX)throw new IllegalStateException("Parcel capacity");
  if(need>data.length){
   int capacity=data.length;
   while(capacity<need){if(capacity>MAX/2){capacity=MAX;break;}capacity*=2;}
   if(capacity>MAX)capacity=MAX;
   if(need>capacity)throw new IllegalStateException("Parcel capacity");
   byte[] next=new byte[capacity];System.arraycopy(data,0,next,0,size);data=next;
  }
  discardObjects(position,need);
  if(need>size)size=need;
 }
 public final int dataPosition(){live();return position;}
 public final int dataSize(){live();return size;}
 public final int dataAvail(){live();return size-position;}
 public final int dataCapacity(){live();return data.length;}
 public final void setDataPosition(int offset){live();if(offset<0||offset>size)throw new IllegalArgumentException("Parcel position");position=offset;}
 public final void setDataSize(int value){live();if(value<0||value>data.length)throw new IllegalArgumentException("Parcel size");if(value<size)discardObjects(value,size);size=value;if(position>size)position=size;}
 public final void writeInt(int value){ensure(4);data[position++]=(byte)value;data[position++]=(byte)(value>>8);data[position++]=(byte)(value>>16);data[position++]=(byte)(value>>24);}
 public final int readInt(){live();if(position+4>size)throw new RuntimeException("Parcel underrun");int value=(data[position]&255)|((data[position+1]&255)<<8)|((data[position+2]&255)<<16)|((data[position+3]&255)<<24);position+=4;return value;}
 public final void writeLong(long value){writeInt((int)value);writeInt((int)(value>>32));}
 public final long readLong(){long low=readInt()&0xffffffffL;long high=readInt();return low|(high<<32);}
 public final void writeFloat(float value){writeInt(Float.floatToIntBits(value));}
 public final float readFloat(){return Float.intBitsToFloat(readInt());}
 public final void writeDouble(double value){writeLong(Double.doubleToLongBits(value));}
 public final double readDouble(){return Double.longBitsToDouble(readLong());}
 public final void writeByte(byte value){ensure(1);data[position++]=value;}
 public final byte readByte(){live();if(position>=size)throw new RuntimeException("Parcel underrun");return data[position++];}
 public final void writeBoolean(boolean value){writeInt(value?1:0);}
 public final boolean readBoolean(){return readInt()!=0;}
 public final void writeString(String value){
  if(value==null){writeInt(-1);return;}
  int length=value.length();writeInt(length);
  ensure(length*2);
  for(int i=0;i<length;i++){char c=value.charAt(i);data[position++]=(byte)c;data[position++]=(byte)(c>>8);}
  if((length&1)!=0){ensure(2);data[position++]=0;data[position++]=0;}
 }
 public final String readString(){
  int length=readInt();if(length<0)return null;
  if(length>MAX/2||position+length*2+((length&1)*2)>size)throw new RuntimeException("Parcel string underrun");
  char[] chars=new char[length];
  for(int i=0;i<length;i++){chars[i]=(char)((data[position]&255)|((data[position+1]&255)<<8));position+=2;}
  if((length&1)!=0)position+=2;
  return new String(chars);
 }
 public final void writeByteArray(byte[] value){
  if(value==null){writeInt(-1);return;}
  writeInt(value.length);ensure(value.length);System.arraycopy(value,0,data,position,value.length);position+=value.length;
 }
 public final byte[] createByteArray(){
  int length=readInt();if(length<0)return null;
  if(length>MAX||position+length>size)throw new RuntimeException("Parcel byte array underrun");
  byte[] value=new byte[length];System.arraycopy(data,position,value,0,length);position+=length;return value;
 }
 public final void readByteArray(byte[] value){
  if(value==null)throw new NullPointerException("value");
  int length=readInt();if(length!=value.length)throw new RuntimeException("Parcel byte array length mismatch");
  if(position+length>size)throw new RuntimeException("Parcel byte array underrun");
  System.arraycopy(data,position,value,0,length);position+=length;
 }
 public final void writeIntArray(int[] value){if(value==null){writeInt(-1);return;}writeInt(value.length);for(int item:value)writeInt(item);}
 public final int[] createIntArray(){int length=readInt();if(length<0)return null;if(length>MAX/4)throw new RuntimeException("Parcel int array limit");int[] value=new int[length];for(int i=0;i<length;i++)value[i]=readInt();return value;}
 public final void readIntArray(int[] value){if(value==null)throw new NullPointerException("value");int length=readInt();if(length!=value.length)throw new RuntimeException("Parcel int array length mismatch");for(int i=0;i<length;i++)value[i]=readInt();}
 public final void writeLongArray(long[] value){if(value==null){writeInt(-1);return;}writeInt(value.length);for(long item:value)writeLong(item);}
 public final long[] createLongArray(){int length=readInt();if(length<0)return null;if(length>MAX/8)throw new RuntimeException("Parcel long array limit");long[] value=new long[length];for(int i=0;i<length;i++)value[i]=readLong();return value;}
 public final void readLongArray(long[] value){if(value==null)throw new NullPointerException("value");int length=readInt();if(length!=value.length)throw new RuntimeException("Parcel long array length mismatch");for(int i=0;i<length;i++)value[i]=readLong();}
 public final void writeFloatArray(float[] value){if(value==null){writeInt(-1);return;}writeInt(value.length);for(float item:value)writeFloat(item);}
 public final float[] createFloatArray(){int length=readInt();if(length<0)return null;if(length>MAX/4)throw new RuntimeException("Parcel float array limit");float[] value=new float[length];for(int i=0;i<length;i++)value[i]=readFloat();return value;}
 public final void readFloatArray(float[] value){if(value==null)throw new NullPointerException("value");int length=readInt();if(length!=value.length)throw new RuntimeException("Parcel float array length mismatch");for(int i=0;i<length;i++)value[i]=readFloat();}
 public final void writeDoubleArray(double[] value){if(value==null){writeInt(-1);return;}writeInt(value.length);for(double item:value)writeDouble(item);}
 public final double[] createDoubleArray(){int length=readInt();if(length<0)return null;if(length>MAX/8)throw new RuntimeException("Parcel double array limit");double[] value=new double[length];for(int i=0;i<length;i++)value[i]=readDouble();return value;}
 public final void readDoubleArray(double[] value){if(value==null)throw new NullPointerException("value");int length=readInt();if(length!=value.length)throw new RuntimeException("Parcel double array length mismatch");for(int i=0;i<length;i++)value[i]=readDouble();}
 public final void writeBooleanArray(boolean[] value){if(value==null){writeInt(-1);return;}writeInt(value.length);for(boolean item:value)writeInt(item?1:0);}
 public final boolean[] createBooleanArray(){int length=readInt();if(length<0)return null;if(length>MAX/4)throw new RuntimeException("Parcel boolean array limit");boolean[] value=new boolean[length];for(int i=0;i<length;i++)value[i]=readInt()!=0;return value;}
 public final void readBooleanArray(boolean[] value){if(value==null)throw new NullPointerException("value");int length=readInt();if(length!=value.length)throw new RuntimeException("Parcel boolean array length mismatch");for(int i=0;i<length;i++)value[i]=readInt()!=0;}
 public final void writeStringArray(String[] value){if(value==null){writeInt(-1);return;}writeInt(value.length);for(String item:value)writeString(item);}
 public final String[] createStringArray(){int length=readInt();if(length<0)return null;if(length>65536)throw new RuntimeException("Parcel string array limit");String[] value=new String[length];for(int i=0;i<length;i++)value[i]=readString();return value;}
 public final void readStringArray(String[] value){if(value==null)throw new NullPointerException("value");int length=readInt();if(length!=value.length)throw new RuntimeException("Parcel string array length mismatch");for(int i=0;i<length;i++)value[i]=readString();}
 public final void writeBundle(Bundle value){writeTypedObject(value,0);}
 public final Bundle readBundle(){return readBundle(null);}
 public final Bundle readBundle(ClassLoader loader){if(readInt()==0)return null;return BundleParcel.read(this,loader);}
 public final void writeParcelable(Parcelable value,int flags){
  if(value==null){writeString(null);return;}
  writeString(value.getClass().getName());value.writeToParcel(this,flags);
 }
 public final <T extends Parcelable> T readParcelable(ClassLoader loader){
  String name=readString();if(name==null)return null;
  try{
   Class<?> type=(loader==null?Class.forName(name):Class.forName(name,true,loader));
   Parcelable.Creator<?> creator=(Parcelable.Creator<?>)type.getField("CREATOR").get(null);
   @SuppressWarnings("unchecked") T value=(T)creator.createFromParcel(this);return value;
  }catch(ReflectiveOperationException error){throw new RuntimeException("Cannot read Parcelable "+name,error);}
 }
 public final <T extends Parcelable> void writeTypedObject(T value,int flags){if(value==null){writeInt(0);return;}writeInt(1);value.writeToParcel(this,flags);}
 public final <T> T readTypedObject(Parcelable.Creator<T> creator){if(readInt()==0)return null;return creator.createFromParcel(this);}
 public final <T extends Parcelable> void writeTypedList(java.util.List<T> value){
  if(value==null){writeInt(-1);return;}
  int length=value.size();writeInt(length);
  for(int i=0;i<length;i++){T item=value.get(i);if(item==null)writeInt(0);else{writeInt(1);item.writeToParcel(this,0);}}
 }
 public final <T> java.util.ArrayList<T> createTypedArrayList(Parcelable.Creator<T> creator){
  int length=readInt();if(length<0)return null;if(length>65536)throw new RuntimeException("Parcel typed list limit");
  java.util.ArrayList<T> value=new java.util.ArrayList<T>(length);
  for(int i=0;i<length;i++)value.add(readInt()!=0?creator.createFromParcel(this):null);
  return value;
 }
 public final <T> void readTypedList(java.util.List<T> list,Parcelable.Creator<T> creator){
  if(list==null)throw new NullPointerException("list");
  int length=readInt();list.clear();if(length<0)return;if(length>65536)throw new RuntimeException("Parcel typed list limit");
  for(int i=0;i<length;i++)list.add(readInt()!=0?creator.createFromParcel(this):null);
 }
 public final <T extends Parcelable> void writeTypedArray(T[] value,int flags){
  if(value==null){writeInt(-1);return;}
  writeInt(value.length);for(T item:value){if(item==null)writeInt(0);else{writeInt(1);item.writeToParcel(this,flags);}}
 }
 public final <T> T[] createTypedArray(Parcelable.Creator<T> creator){
  int length=readInt();if(length<0)return null;if(length>65536)throw new RuntimeException("Parcel typed array limit");
  T[] value=creator.newArray(length);for(int i=0;i<length;i++)value[i]=readInt()!=0?creator.createFromParcel(this):null;return value;
 }
 public final <T> void readTypedArray(T[] value,Parcelable.Creator<T> creator){
  if(value==null)throw new NullPointerException("value");
  int length=readInt();if(length!=value.length)throw new RuntimeException("Parcel typed array length mismatch");
  for(int i=0;i<length;i++)value[i]=readInt()!=0?creator.createFromParcel(this):null;
 }
 public final void writeParcelableList(java.util.List<? extends Parcelable> value,int flags){
  if(value==null){writeInt(-1);return;}
  int length=value.size();writeInt(length);for(int i=0;i<length;i++)writeParcelable(value.get(i),flags);
 }
 public final <T extends Parcelable> java.util.ArrayList<T> readParcelableList(java.util.List<T> list,ClassLoader loader){
  int length=readInt();if(length<0){if(list!=null)list.clear();return null;}
  if(length>65536)throw new RuntimeException("Parcel parcelable list limit");
  java.util.ArrayList<T> out=list instanceof java.util.ArrayList?(java.util.ArrayList<T>)list:new java.util.ArrayList<T>(length);
  out.clear();for(int i=0;i<length;i++)out.add(this.<T>readParcelable(loader));return out;
 }
 public static Parcel obtain(IBinder binder){return obtain();}
 public final void writeInterfaceToken(String descriptor){if(descriptor==null)throw new NullPointerException("descriptor");writeString(descriptor);}
 public final void enforceInterface(String descriptor){if(descriptor==null||!descriptor.equals(readString()))throw new SecurityException("Binder interface mismatch");}
 public final void enforceNoDataAvail(){if(dataAvail()!=0)throw new IllegalArgumentException("Unexpected trailing parcel data");}
 public final void writeNoException(){writeInt(0);}
 public final void writeException(Exception exception){
  int code=exception instanceof SecurityException?-1:exception instanceof IllegalArgumentException?-3:
   exception instanceof NullPointerException?-4:exception instanceof IllegalStateException?-5:
   exception instanceof UnsupportedOperationException?-7:0;
  if(code==0)throw new RuntimeException(exception);
  writeInt(code);writeString(exception.getMessage());
 }
 public final int readExceptionCode(){return readInt();}
 public final void readException(){int code=readExceptionCode();if(code!=0)readException(code,readString());}
 public final void readException(int code,String message){
  switch(code){case 0:return;case -1:throw new SecurityException(message);case -3:throw new IllegalArgumentException(message);
   case -4:throw new NullPointerException(message);case -5:throw new IllegalStateException(message);case -7:throw new UnsupportedOperationException(message);
   default:throw new RuntimeException("Remote exception "+code+": "+message);}
 }
 private void writeObject(Object value,int marker){
  live();if(objects.size()>=1024&&!objects.containsKey(position))throw new IllegalStateException("Parcel attachment limit");
  int offset=position;writeInt(marker);objects.put(offset,value);
 }
 private Object readObject(int marker,Class<?> type){
  int offset=position;int value=readInt();
  if(value==0&&type==IBinder.class)return null;
  Object object=objects.get(offset);
  if(value!=marker||!type.isInstance(object))throw new IllegalArgumentException("Invalid parcel attachment");
  return object;
 }
 public final void writeStrongBinder(IBinder binder){if(binder==null)writeInt(0);else writeObject(binder,0x42494e44);}
 public final void writeStrongInterface(IInterface owner){writeStrongBinder(owner==null?null:owner.asBinder());}
 public final IBinder readStrongBinder(){return (IBinder)readObject(0x42494e44,IBinder.class);}
 public final boolean hasFileDescriptors(){live();for(Object value:objects.values())if(value instanceof ParcelFileDescriptor)return true;return false;}
 public final void writeFileDescriptor(java.io.FileDescriptor descriptor){
  ParcelFileDescriptor copy;
  try{copy=ParcelFileDescriptor.dup(descriptor);}catch(java.io.IOException error){throw new IllegalStateException("Cannot duplicate parcel descriptor",error);}
  boolean stored=false;
  try{writeObject(copy,0x46494c45);stored=true;}finally{if(!stored)try{copy.close();}catch(java.io.IOException ignored){}}
 }
 public final ParcelFileDescriptor readFileDescriptor(){
  ParcelFileDescriptor stored=(ParcelFileDescriptor)readObject(0x46494c45,ParcelFileDescriptor.class);
  try{return stored.dup();}catch(java.io.IOException error){throw new IllegalStateException("Cannot duplicate received descriptor",error);}
 }
 // Package-private capability access for the private Binder transport. Raw bytes
 // alone never grant a Binder handle or descriptor: each slot is validated here.
 byte[] transportBytes(){live();return java.util.Arrays.copyOf(data,size);}
 java.util.Map<Integer,Object> transportObjects(){live();return new java.util.TreeMap<Integer,Object>(objects);}
 void transportAttach(int offset,Object value){
  live();if(offset<0||offset>size-4||objects.size()>=1024)throw new IllegalArgumentException("Invalid attachment offset");
  Integer before=objects.floorKey(offset),after=objects.ceilingKey(offset);
  if(before!=null&&before+4>offset||after!=null&&after<offset+4)throw new IllegalArgumentException("Overlapping parcel attachments");
  int marker=(data[offset]&255)|((data[offset+1]&255)<<8)|((data[offset+2]&255)<<16)|((data[offset+3]&255)<<24);
  if(!(value instanceof IBinder&&marker==0x42494e44)&&!(value instanceof ParcelFileDescriptor&&marker==0x46494c45))
   throw new IllegalArgumentException("Attachment type mismatch");
  objects.put(offset,value);
 }
 void transportMoveFrom(Parcel source){
  live();source.live();if(source==this)return;
  byte[] replacement=new byte[64];discardObjects(0,MAX);
  java.util.TreeMap<Integer,Object> empty=objects;objects=source.objects;source.objects=empty;
  data=source.data;size=source.size;position=0;source.data=replacement;source.size=source.position=0;
 }
 public final byte[] marshall(){live();if(!objects.isEmpty())throw new IllegalStateException("Cannot marshall Binder or descriptor attachments as bytes");byte[] out=new byte[size];System.arraycopy(data,0,out,0,size);return out;}
 public final void unmarshall(byte[] buffer,int offset,int length){
  live();if(buffer==null)throw new NullPointerException("buffer");
  if(offset<0||length<0||offset>buffer.length-length||length>MAX)throw new IllegalArgumentException("Parcel buffer");
  if(data.length<length)data=new byte[Math.min(MAX,Math.max(64,length))];
  discardObjects(0,MAX);System.arraycopy(buffer,offset,data,0,length);size=length;position=0;
 }
}
