// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.*;
import android.app.*;
import android.os.*;
import java.util.*;
import android.os.Parcel;
import java.nio.ByteBuffer;
import java.util.Arrays;
public final class ServiceRedeliverySmoke {
 static final String PKG="org.shiraz.redelivery",COMP=Worker.class.getName();
 static final List<Integer> delivered=new ArrayList<Integer>();
 public static final class Worker extends Service {
  public IBinder onBind(Intent intent){return null;}
  public int onStartCommand(Intent intent,int flags,int id){
   check(flags==START_FLAG_REDELIVERY,"redelivery flag missing");
   delivered.add(id);
   check(stopSelfResult(id)==(id==5),"stop ID does not track latest retained work");
   return START_NOT_STICKY;
  }
 }
 static void lifecycle(ServiceRedelivery work){
  Looper.prepareMainLooper();
  Context context=new ContextWrapper(null){public String getPackageName(){return PKG;}public ClassLoader getClassLoader(){return ServiceRedeliverySmoke.class.getClassLoader();}};
  Properties metadata=new Properties();metadata.setProperty("service.count","1");metadata.setProperty("service.0.name",COMP);
  HostServices host=new HostServices(context,new Application(),metadata);
  final List<Integer> finished=new ArrayList<Integer>();
  ComponentName name=new ComponentName(PKG,COMP);
  try{
   host.createWorkerService(name,new HostServices.StartedListener(){public void changed(boolean started){}public void finished(int id){finished.add(id);}});
   host.restartRedeliveryWorker(name,work);
   try{host.restartRedeliveryWorker(name,work);throw new AssertionError("Duplicate restart queued");}catch(IllegalStateException expected){}
   for(int i=0;i<5;i++)Looper.myLooper().shirazDispatchDue(100);
   check(delivered.equals(Arrays.asList(2,5)),"retained starts lost or renumbered");
   check(finished.equals(Arrays.asList(2,5)),"completion IDs lost");
  }finally{host.close();Looper.shirazShutdownMain();}
 }
 static byte[] raw(Intent intent){Parcel p=Parcel.obtain();try{intent.writeToParcel(p,0);return p.marshall();}finally{p.recycle();}}
 static byte[] frame(int[] ids,byte[][] items){int size=8;for(byte[] item:items)size+=8+item.length;ByteBuffer b=ByteBuffer.allocate(size);b.putInt(2).putInt(ids.length);for(int i=0;i<ids.length;i++)b.putInt(ids[i]).putInt(items[i].length).put(items[i]);return b.array();}
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void reject(byte[] bytes){try{ServiceRedelivery.decode(bytes,PKG,COMP);throw new AssertionError("Malformed frame accepted");}catch(RuntimeException expected){}}
 public static void main(String[] args){
  byte[] raw=raw(new Intent("first").setClassName(PKG,COMP).putExtra("payload","retained").putExtra("array",new int[]{2,7}));
  byte[] valid=frame(new int[]{2,5},new byte[][]{raw,raw});
  ServiceRedelivery result=ServiceRedelivery.decode(valid,PKG,COMP);
  check(Arrays.equals(result.ids,new int[]{2,5}),"start IDs");
  check("first".equals(result.intents[0].getAction())&&"retained".equals(result.intents[1].getStringExtra("payload")),"intent fields");
  check(Arrays.equals(result.intents[1].getIntArrayExtra("array"),new int[]{2,7}),"array payload");
  result.intents[0].getIntArrayExtra("array")[0]=99;
  check(result.intents[1].getIntArrayExtra("array")[0]==2,"replayed intents alias");
  reject(null);reject(new byte[0]);reject(new byte[32768+8+64*8+1]);
  reject(frame(new int[]{},new byte[][]{}));
  reject(frame(new int[]{0},new byte[][]{raw}));
  reject(frame(new int[]{2,2},new byte[][]{raw,raw}));
  reject(frame(new int[]{2,1},new byte[][]{raw,raw}));
  reject(frame(new int[]{1},new byte[][]{new byte[0]}));
  reject(frame(new int[]{1},new byte[][]{Arrays.copyOf(raw,raw.length+1)}));
  reject(frame(new int[]{1},new byte[][]{raw(new Intent().setClassName("wrong",COMP))}));
  reject(frame(new int[]{1},new byte[][]{raw(new Intent().setClassName(PKG,"wrong"))}));
  reject(frame(new int[]{1},new byte[][]{raw(new Intent().setClassName(PKG,COMP).setPackage("wrong"))}));
  reject(frame(new int[]{1},new byte[][]{raw(new Intent())}));
  reject(Arrays.copyOf(valid,valid.length-1));reject(Arrays.copyOf(valid,valid.length+1));
  byte[] changed=valid.clone();ByteBuffer.wrap(changed).putInt(1);reject(changed);
  changed=valid.clone();ByteBuffer.wrap(changed).putInt(4,65);reject(changed);
  changed=valid.clone();ByteBuffer.wrap(changed).putInt(12,Integer.MAX_VALUE);reject(changed);
  lifecycle(ServiceRedelivery.decode(valid,PKG,COMP));
  System.out.println("SERVICE_REDELIVERY_OK");
 }
}
