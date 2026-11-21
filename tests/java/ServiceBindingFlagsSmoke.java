// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.HostServices;
import java.util.Properties;
public final class ServiceBindingFlagsSmoke {
 static int creates,binds,unbinds,destroys;
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static class Sample extends Service{
  public void onCreate(){creates++;}
  public IBinder onBind(Intent intent){binds++;return new Binder();}
  public boolean onUnbind(Intent intent){unbinds++;return false;}
  public void onDestroy(){destroys++;}
 }
 static class Screen extends Activity{Screen(Context base){attachBaseContext(base);}}
 static void due(){for(int i=0;i<8;i++)Looper.myLooper().shirazDispatchDue(64);}
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();final HostServices[] host={null};final Application app=new Application();
  Context context=new ContextWrapper(null){
   public String getPackageName(){return "test.bind";}
   public ClassLoader getClassLoader(){return ServiceBindingFlagsSmoke.class.getClassLoader();}
   public Context getApplicationContext(){return app;}
   public int checkSelfPermission(String name){return android.content.pm.PackageManager.PERMISSION_DENIED;}
   protected HostServices shirazServices(){return host[0];}
  };
  Properties metadata=new Properties();metadata.setProperty("service.count","1");metadata.setProperty("service.0.name",Sample.class.getName());
  Intent intent=new Intent().setComponent(new ComponentName("test.bind",Sample.class.getName()));
  final int[] connected={0};ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder binder){connected[0]++;check(binder!=null,"Missing binder");}public void onServiceDisconnected(ComponentName name){throw new AssertionError("Unexpected disconnect");}};
  try{
   host[0]=new HostServices(context,app,metadata);
   for(Context caller:new Context[]{context,new ContextWrapper(context),new Screen(context)}){
    check(caller.bindService(intent,connection,4225),"GMS binding rejected");due();
    check(caller.checkSelfPermission("android.permission.CAMERA")!=0,"Permission inherited on API27");
    caller.unbindService(connection);due();
   }
   check(connected[0]==3&&creates==3&&binds==3&&unbinds==3&&destroys==3,"Flagged lifecycle incomplete");
   try{context.bindService(intent,connection,4224);throw new AssertionError("Missing AUTO_CREATE accepted");}catch(UnsupportedOperationException expected){}
   try{context.bindService(intent,connection,0x2001);throw new AssertionError("Unknown flag accepted");}catch(UnsupportedOperationException expected){}
   host[0].close();metadata.setProperty("service.0.process",":remote");host[0]=new HostServices(context,app,metadata);
   try{new ContextWrapper(new Screen(context)).bindService(intent,connection,4225);throw new AssertionError("Remote Activity priority falsely supported");}catch(UnsupportedOperationException expected){check(expected.getMessage().contains("Remote activity"),"Wrong rejection");}
   due();check(creates==3,"Rejected binding created service");
   System.out.println("SERVICE_BINDING_FLAGS_OK");
  }finally{if(host[0]!=null)host[0].close();Looper.shirazShutdownMain();}
 }
}
