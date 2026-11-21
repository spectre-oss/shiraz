// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.HostServices;
import java.util.*;
public final class ServiceStartResultSmoke {
 public static final class Worker extends Service {
  public IBinder onBind(Intent intent){return null;}
  public int onStartCommand(Intent intent,int flags,int id){if(intent.getBooleanExtra("stop",false))stopSelf();int mode=intent.getIntExtra("mode",START_NOT_STICKY);intent.setAction("mutated");intent.putExtra("mode",99);return mode;}
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void drain(){for(int i=0;i<5;i++)Looper.myLooper().shirazDispatchDue(100);}
 public static void main(String[] args){
  Looper.prepareMainLooper();final HostServices[] host=new HostServices[1];
  Context context=new ContextWrapper(null){public String getPackageName(){return "org.shiraz.startresult";}public ClassLoader getClassLoader(){return ServiceStartResultSmoke.class.getClassLoader();}protected HostServices shirazServices(){return host[0];}};
  Properties metadata=new Properties();metadata.setProperty("service.count","1");metadata.setProperty("service.0.name",Worker.class.getName());
  host[0]=new HostServices(context,new Application(),metadata);final List<String> events=new ArrayList<>();
  ComponentName name=new ComponentName(context.getPackageName(),Worker.class.getName());
  try{
   Service worker=host[0].createWorkerService(name,new HostServices.StartedListener(){public void changed(boolean started){events.add("started:"+started);}public void completed(int id,int mode,Intent original){check(original.getAction()==null&&original.getIntExtra("mode",-1)==mode,"callback mutated redelivery snapshot");events.add(id+":"+mode);}public void finished(int id){events.add("finished:"+id);}});
   for(int mode=0;mode<4;mode++){host[0].start(new Intent().setComponent(name).putExtra("mode",mode));drain();}
   check(events.equals(Arrays.asList("started:true","1:0","2:1","3:2","4:3")),"start completion IDs/modes lost: "+events);
   check(!worker.stopSelfResult(2),"earlier completion stopped newer start");
   check(events.get(5).equals("finished:2"),"earlier completion missing");
   check(!worker.stopSelfResult(1)&&!worker.stopSelfResult(2)&&!worker.stopSelfResult(99),"invalid/stale stop result");
   check(events.size()==6,"duplicate or future work acknowledged");
   host[0].start(new Intent().setComponent(name).putExtra("stop",true).putExtra("mode",Service.START_STICKY));drain();
   check(events.size()==7&&events.get(6).equals("started:false"),"stopSelf incorrectly retained restart mode");
   System.out.println("SERVICE_START_RESULT_OK");
  }finally{host[0].close();Looper.shirazShutdownMain();}
 }
}
