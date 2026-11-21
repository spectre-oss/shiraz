// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.HostServices;
import java.util.*;
public final class ServiceBindingFilterSmoke {
 static int creates,destroys;static final List<String> binds=new ArrayList<String>(),unbinds=new ArrayList<String>(),rebinds=new ArrayList<String>();
 public static class Worker extends Service {
  public void onCreate(){creates++;}
  public IBinder onBind(Intent i){binds.add(i.getAction());i.setAction("mutated-by-service");return new Binder();}
  public boolean onUnbind(Intent i){unbinds.add(i.getAction());i.setAction("mutated-unbind");return true;}
  public void onRebind(Intent i){rebinds.add(i.getAction());}
  public void onDestroy(){destroys++;}
 }
 static class Connection implements ServiceConnection {IBinder binder;int calls;public void onServiceConnected(ComponentName n,IBinder b){binder=b;calls++;}public void onServiceDisconnected(ComponentName n){throw new AssertionError("disconnected");}}
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void pump(){for(int i=0;i<8;i++)Looper.myLooper().shirazDispatchDue(256);}
 public static void main(String[] args){
  Looper.prepareMainLooper();final HostServices[] host=new HostServices[1];
  Context context=new ContextWrapper(null){public String getPackageName(){return "org.shiraz.filters";}public ClassLoader getClassLoader(){return ServiceBindingFilterSmoke.class.getClassLoader();}protected HostServices shirazServices(){return host[0];}};
  Properties meta=new Properties();meta.setProperty("service.count","1");meta.setProperty("service.0.name",Worker.class.getName());host[0]=new HostServices(context,new Application(),meta);
  Intent a=new Intent("first").setClassName(context.getPackageName(),Worker.class.getName()),b=new Intent("second").setClassName(context.getPackageName(),Worker.class.getName());
  Connection one=new Connection(),two=new Connection(),same=new Connection();
  try{
   context.bindService(a,one,1);context.bindService(b,two,1);context.bindService(new Intent(a).putExtra("ignored",1),same,1);pump();
   check(creates==1&&binds.size()==2,"distinct filters must bind on one service instance");
   check(one.binder==same.binder&&one.binder!=two.binder,"wrong Binder shared across filters");
   context.unbindService(one);pump();check(unbinds.isEmpty(),"filter still has a client");
   context.unbindService(same);pump();check(unbinds.equals(Arrays.asList("first"))&&destroys==0,"filter-local unbind missing");
   Connection again=new Connection();context.bindService(a,again,1);pump();check(rebinds.equals(Arrays.asList("first"))&&again.binder==one.binder,"filter rebind lost binder");
   context.unbindService(two);pump();check(unbinds.equals(Arrays.asList("first","second"))&&destroys==0,"unbound wrong filter");
   context.unbindService(again);pump();check(destroys==1&&!host[0].hasWork(),"last filter did not release service");
   Connection x=new Connection(),y=new Connection();context.bindService(a,x,1);context.bindService(b,y,1);pump();
   int before=unbinds.size();context.unbindService(x);context.unbindService(y);pump();
   check(unbinds.size()==before+2&&destroys==2&&!host[0].hasWork(),"batched unbind lost a filter callback");
   System.out.println("SERVICE_BINDING_FILTER_OK");
  }finally{host[0].close();Looper.shirazShutdownMain();}
 }
}
