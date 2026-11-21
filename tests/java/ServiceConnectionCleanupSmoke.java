// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.HostServices;
import java.util.Properties;
public final class ServiceConnectionCleanupSmoke {
 static int binds,unbinds,destroys;
 public static class First extends Service {
  public IBinder onBind(Intent intent){binds++;return new Binder();}
  public boolean onUnbind(Intent intent){unbinds++;return false;}
  public void onDestroy(){destroys++;}
 }
 public static final class Second extends First {}
 static final class Connection implements ServiceConnection {
  int connected;
  public void onServiceConnected(ComponentName name,IBinder value){connected++;}
  public void onServiceDisconnected(ComponentName name){throw new AssertionError("Unexpected disconnect");}
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void drain(){for(int i=0;i<5;i++)Looper.myLooper().shirazDispatchDue(100);}
 public static void main(String[] args){
  Looper.prepareMainLooper();final HostServices[] host=new HostServices[1];
  Context context=new ContextWrapper(null){
   public String getPackageName(){return "org.shiraz.connectioncleanup";}
   public ClassLoader getClassLoader(){return ServiceConnectionCleanupSmoke.class.getClassLoader();}
   protected HostServices shirazServices(){return host[0];}
  };
  Properties metadata=new Properties();metadata.setProperty("service.count","2");metadata.setProperty("service.0.name",First.class.getName());metadata.setProperty("service.1.name",Second.class.getName());
  host[0]=new HostServices(context,new Application(),metadata);
  Intent first=new Intent().setComponent(new ComponentName(context.getPackageName(),First.class.getName()));
  Intent second=new Intent().setComponent(new ComponentName(context.getPackageName(),Second.class.getName()));
  try{
   Connection shared=new Connection();
   check(context.bindService(first,shared,Context.BIND_AUTO_CREATE)&&context.bindService(second,shared,Context.BIND_AUTO_CREATE),"binding rejected");drain();
   check(shared.connected==2&&binds==2,"missing service connection");
   context.unbindService(shared);drain();
   check(unbinds==2&&destroys==2&&!host[0].hasWork(),"one unbind left another component bound");
   try{context.unbindService(shared);throw new AssertionError("duplicate unbind accepted");}catch(IllegalArgumentException expected){}
   Connection other=new Connection();
   context.bindService(first,shared,Context.BIND_AUTO_CREATE);context.bindService(second,shared,Context.BIND_AUTO_CREATE);context.bindService(first,other,Context.BIND_AUTO_CREATE);drain();
   context.unbindService(shared);drain();
   check(unbinds==3&&destroys==3&&host[0].hasWork(),"shared unbind released another client's service");
   context.unbindService(other);drain();check(unbinds==4&&destroys==4&&!host[0].hasWork(),"last client cleanup failed");
   int delivered=shared.connected;
   context.bindService(first,shared,Context.BIND_AUTO_CREATE);context.bindService(second,shared,Context.BIND_AUTO_CREATE);
   context.unbindService(shared);drain();
   check(shared.connected==delivered&&!host[0].hasWork(),"queued connection delivered after unbind");
   System.out.println("SERVICE_CONNECTION_CLEANUP_OK");
  }finally{host[0].close();Looper.shirazShutdownMain();}
 }
}
