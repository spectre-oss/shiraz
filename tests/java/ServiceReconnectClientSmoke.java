// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.Application;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.Properties;
/** Actual HostServices callbacks over broker-issued ART endpoints. */
public final class ServiceReconnectClientSmoke {
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();RuntimeBroker.attach();final HostServices[] host=new HostServices[1];
  Context context=new ContextWrapper(null){
   public String getPackageName(){return args[0];}
   public ClassLoader getClassLoader(){return ServiceReconnectClientSmoke.class.getClassLoader();}
   protected HostServices shirazServices(){return host[0];}
  };
  host[0]=new HostServices(context,new Application(),new Properties());
  final boolean[] unbindOnDeath={false};
  ServiceConnection connection=new ServiceConnection(){int connects;
   public void onServiceConnected(ComponentName name,IBinder binder){
    Parcel input=Parcel.obtain(),output=Parcel.obtain();
    try{input.writeInterfaceToken("shiraz.fixture.service");if(!binder.transact(8,input,output,0))throw new AssertionError("PID query rejected");output.readException();int pid=output.readInt();output.readInt();output.readInt();output.enforceNoDataAvail();System.out.println("RESULT "+pid+" "+(++connects));System.out.flush();}
    catch(RemoteException failure){throw new AssertionError(failure);}finally{input.recycle();output.recycle();}
   }
   public void onServiceDisconnected(ComponentName name){
    if(unbindOnDeath[0]){context.unbindService(this);System.out.println("UNBOUND");}
    else System.out.println("DISCONNECTED");System.out.flush();
   }
   public void onBindingDied(ComponentName name){throw new AssertionError("Binding died instead of reconnecting");}
  };
  if(!context.bindService(new Intent().setClassName("org.shiraz.servicefixture","org.shiraz.servicefixture.Worker"),connection,Context.BIND_AUTO_CREATE))throw new AssertionError("Bind rejected");
  Handler main=new Handler(Looper.getMainLooper());
  Thread input=new Thread(new Runnable(){public void run(){try{BufferedReader reader=new BufferedReader(new InputStreamReader(System.in));String command;while((command=reader.readLine())!=null){final String action=command;main.post(new Runnable(){public void run(){if(action.equals("unbind-on-death")){unbindOnDeath[0]=true;System.out.println("READY");System.out.flush();}else if(action.equals("close")){host[0].close();try{RuntimeBroker.close();}catch(IOException failure){throw new AssertionError(failure);}Looper.shirazRequestMainExit();}else throw new AssertionError("Unknown command");}});}}catch(IOException failure){throw new AssertionError(failure);}}},"fixture-input");input.setDaemon(true);input.start();
  try{Looper.loop();}finally{host[0].close();RuntimeBroker.close();}
 }
}
