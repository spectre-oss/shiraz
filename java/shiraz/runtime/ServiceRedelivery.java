// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Intent;
import android.os.Parcel;
import java.nio.ByteBuffer;
/** Strict decoder for the broker's descriptor-free, bounded restart control. */
final class ServiceRedelivery {
 final int[] ids;
 final Intent[] intents;
 private ServiceRedelivery(int count){ids=new int[count];intents=new Intent[count];}
 static ServiceRedelivery decode(byte[] bytes,String pkg,String component){
  if(bytes==null||bytes.length<8||bytes.length>32768+8+64*8)throw new IllegalArgumentException("Redelivery frame size");
  ByteBuffer input=ByteBuffer.wrap(bytes);
  if(input.getInt()!=2)throw new IllegalArgumentException("Redelivery control kind");
  int count=input.getInt();
  if(count<1||count>64)throw new IllegalArgumentException("Redelivery count");
  ServiceRedelivery result=new ServiceRedelivery(count);int previous=0,total=0;
  for(int i=0;i<count;i++){
   if(input.remaining()<8)throw new IllegalArgumentException("Truncated redelivery entry");
   int id=input.getInt(),length=input.getInt();
   if(id<=previous||length<1||length>32768-total||length>input.remaining())throw new IllegalArgumentException("Redelivery entry bounds");
   previous=id;total+=length;byte[] raw=new byte[length];input.get(raw);
   Parcel parcel=Parcel.obtain();
   try{
    parcel.unmarshall(raw,0,raw.length);parcel.setDataPosition(0);
    Intent intent=Intent.CREATOR.createFromParcel(parcel);parcel.enforceNoDataAvail();
    if(intent.getComponent()==null||!pkg.equals(intent.getComponent().getPackageName())||!component.equals(intent.getComponent().getClassName())||(intent.getPackage()!=null&&!pkg.equals(intent.getPackage())))throw new SecurityException("Redelivery target mismatch");
    result.ids[i]=id;result.intents[i]=intent.shirazBroadcastCopy();
   }finally{parcel.recycle();}
  }
  if(input.hasRemaining())throw new IllegalArgumentException("Trailing redelivery bytes");
  return result;
 }
}
