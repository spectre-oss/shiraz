// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.Application;
import android.content.*;
import android.os.*;
import java.util.Properties;
public final class RemoteServiceFilterSmoke {
 static class Connection implements ServiceConnection {IBinder binder;int connects,disconnects;public void onServiceConnected(ComponentName n,IBinder b){binder=b;connects++;}public void onServiceDisconnected(ComponentName n){binder=null;disconnects++;}public void onBindingDied(ComponentName n){throw new AssertionError("binding died");}}
 static void await(int count,Connection... clients)throws Exception{long end=System.nanoTime()+20000000000L;while(true){Looper.myLooper().shirazDispatchDue(256);boolean done=true;for(Connection c:clients)done&=c.binder!=null&&c.connects>=count;if(done)return;if(System.nanoTime()>end)throw new AssertionError("bind timeout");Thread.sleep(5);}}
 static int[] query(Connection client,String action)throws Exception{Parcel in=Parcel.obtain(),out=Parcel.obtain();try{in.writeInterfaceToken("shiraz.fixture.service");if(!client.binder.transact(9,in,out,0))throw new AssertionError("filter query");out.readException();if(!action.equals(out.readString()))throw new AssertionError("wrong remote binding filter");if(!"original".equals(out.readString()))throw new AssertionError("bind extras changed across clients or reconnect");int[] ids={out.readInt(),out.readInt(),out.readInt()};out.enforceNoDataAvail();return ids;}finally{in.recycle();out.recycle();}}
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();RuntimeBroker.attach();final HostServices[] host=new HostServices[1];
  Context context=new ContextWrapper(null){public String getPackageName(){return args[0];}protected HostServices shirazServices(){return host[0];}};
  host[0]=new HostServices(context,new Application(),new Properties());
  Intent a=new Intent("first").setClassName("org.shiraz.servicefixture","org.shiraz.servicefixture.Worker"),b=new Intent("second").setClassName("org.shiraz.servicefixture","org.shiraz.servicefixture.Worker");
  a.putExtra("payload","original");b.putExtra("payload","original");
  Connection one=new Connection(),two=new Connection(),same=new Connection();
  try{
   for(Intent absent:new Intent[]{new Intent().setClassName("org.shiraz.missing","org.shiraz.missing.Worker"),new Intent().setClassName("org.shiraz.servicefixture","org.shiraz.servicefixture.Undeclared"),new Intent("missing.action").setPackage("org.shiraz.servicefixture")}){
    if(context.startService(absent)!=null)throw new AssertionError("missing service started");
    if(context.startForegroundService(absent)!=null)throw new AssertionError("missing foreground service started");
    if(context.stopService(absent))throw new AssertionError("missing service stopped");
    Connection missing=new Connection();if(context.bindService(absent,missing,1))throw new AssertionError("missing service bound");context.unbindService(missing);if(missing.connects!=0||missing.disconnects!=0)throw new AssertionError("missing service callbacks");
   }
   context.bindService(a,one,1);context.bindService(b,two,1);context.bindService(new Intent(a).putExtra("payload","later-client"),same,1);await(1,one,two,same);
   int[] x=query(one,"first"),y=query(two,"second"),z=query(same,"first");
   if(x[0]!=y[0]||x[1]!=y[1]||x[2]==y[2]||x[2]!=z[2])throw new AssertionError("remote instance/filter sharing");
   System.out.println("RESULT "+x[0]);System.out.flush();
   if(!"restart".equals(new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine()))throw new AssertionError("restart command missing");
   await(2,one,two,same);
   int oldPid=x[0];x=query(one,"first");y=query(two,"second");z=query(same,"first");
   if(x[0]==oldPid||x[0]!=y[0]||x[1]!=y[1]||x[2]==y[2]||x[2]!=z[2])throw new AssertionError("reconnected filter identity");
   for(Connection client:new Connection[]{one,two,same})if(client.disconnects!=1||client.connects!=2)throw new AssertionError("reconnect callback counts");
   System.out.println("REMOTE_SERVICE_FILTER_RECONNECTED_OK");
   context.unbindService(one);context.unbindService(same);Looper.myLooper().shirazDispatchDue(256);query(two,"second");
   Connection again=new Connection();context.bindService(new Intent(b).putExtra("ignored",2),again,1);await(1,again);
   if(query(again,"second")[2]!=y[2])throw new AssertionError("surviving filter binder changed");
   context.unbindService(two);context.unbindService(again);Looper.myLooper().shirazDispatchDue(256);
   if(host[0].hasWork())throw new AssertionError("remote filter lifetime leaked");
   System.out.println("REMOTE_SERVICE_FILTER_OK");
  }finally{host[0].close();RuntimeBroker.close();Looper.shirazShutdownMain();}
 }
}
