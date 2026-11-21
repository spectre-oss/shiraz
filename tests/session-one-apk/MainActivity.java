// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.sessionfixture;
import android.app.Activity;
import android.content.*;
import android.os.*;
public final class MainActivity extends Activity {
 private boolean complete;
 protected void onCreate(Bundle state){
  super.onCreate(state);android.widget.TextView view=new android.widget.TextView(this);view.setText("Shared host session fixture");setContentView(view);
  try{
   java.lang.reflect.Method client=Class.forName("shiraz.runtime.RuntimeBroker").getDeclaredMethod("client");client.setAccessible(true);Object broker=client.invoke(null);
   java.lang.reflect.Method resolve=broker.getClass().getMethod("resolveReceiver",String.class,String.class,String.class);
   Object receiver=resolve.invoke(broker,"org.shiraz.sessiontwo","org.shiraz.sessionfixture.ExportedReceiver",null);
   if(!"org.shiraz.sessiontwo".equals(receiver.getClass().getField("packageName").get(receiver))||receiver.getClass().getField("uid").getInt(receiver)==android.os.Process.myUid()||!((String)receiver.getClass().getField("apkSha256").get(receiver)).matches("[0-9a-f]{64}"))throw new AssertionError("Receiver identity");
   try{resolve.invoke(broker,"org.shiraz.sessiontwo","org.shiraz.sessionfixture.ManifestReceiver",null);throw new AssertionError("Private receiver accepted");}catch(java.lang.reflect.InvocationTargetException expected){if(!(expected.getCause() instanceof SecurityException))throw expected;}
   try{resolve.invoke(broker,"org.shiraz.sessiontwo","org.shiraz.sessionfixture.ExportedReceiver","test.MISSING");throw new AssertionError("Recipient permission ignored");}catch(java.lang.reflect.InvocationTargetException expected){if(!(expected.getCause() instanceof SecurityException))throw expected;}
   System.out.println("SESSION_RECEIVER_BROKER_RESOLUTION_OK");
  }catch(ReflectiveOperationException error){throw new AssertionError(error);}
  Intent broadcast=new Intent("org.shiraz.CROSS_BROADCAST").setComponent(new ComponentName("org.shiraz.sessiontwo","org.shiraz.sessionfixture.ExportedReceiver"));
  broadcast.putExtra("payload","java-broadcast");
  sendBroadcast(new Intent().setComponent(new ComponentName("org.shiraz.sessiontwo","org.shiraz.sessionfixture.ManifestReceiver")));
  sendBroadcast(broadcast,"test.MISSING");
  sendBroadcast(new Intent().setComponent(new ComponentName("org.shiraz.sessiontwo","org.shiraz.sessionfixture.MissingReceiver")));
  sendBroadcast(new Intent().setComponent(new ComponentName("org.shiraz.uninstalled","org.shiraz.uninstalled.Receiver")));
  sendBroadcast(new Intent().setComponent(new ComponentName("org.shiraz.sessiontwo","org.shiraz.sessionfixture.RemoteReceiver")));
  sendBroadcast(broadcast);broadcast.putExtra("payload","mutated-after-send");
  System.out.println("SESSION_RECEIVER_BROADCAST_ENQUEUED");
  ServiceConnection connection=new ServiceConnection(){
   public void onServiceConnected(ComponentName name,IBinder binder){
    Parcel input=Parcel.obtain(),output=Parcel.obtain();
    try{
     input.writeInterfaceToken("shiraz.fixture.service");if(!binder.transact(6,input,output,0))throw new AssertionError("Barrier method");output.readException();
     int pid=output.readInt(),instance=output.readInt(),callers=output.readInt(),uid=output.readInt();output.enforceNoDataAvail();
     if(callers!=2||uid!=android.os.Process.myUid())throw new AssertionError("Session caller identity");
     System.out.println("SESSION_SHARED_RESULT "+getPackageName()+" "+pid+" "+instance+" "+uid);complete=true;
    }catch(RemoteException error){throw new AssertionError(error);}finally{input.recycle();output.recycle();unbindService(this);}
    Intent dynamic=new Intent("org.shiraz.DYNAMIC_BROADCAST").setPackage("org.shiraz.sessiontwo").addCategory("org.shiraz.DYNAMIC_CATEGORY").addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY).putExtra("payload","dynamic-snapshot");
    sendBroadcast(dynamic,"test.MISSING");
    sendBroadcast(new Intent(dynamic).addCategory("org.shiraz.UNKNOWN"));
    sendBroadcast(dynamic,"org.shiraz.sessiontwo.RECEIVE");dynamic.putExtra("payload","mutated");
    Intent implicit=new Intent("org.shiraz.LIVE_BROADCAST").setPackage("org.shiraz.sessiontwo");
    sendBroadcast(implicit,"test.MISSING");
    sendBroadcast(new Intent(implicit).addCategory("org.shiraz.UNKNOWN"));
    sendBroadcast(new Intent(implicit).addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY));
    sendBroadcast(implicit,"org.shiraz.sessiontwo.RECEIVE");
    finish();
   }
   public void onServiceDisconnected(ComponentName name){throw new AssertionError("Disconnected");}
   public void onBindingDied(ComponentName name){throw new AssertionError("Binding died");}
  };
  if(!bindService(new Intent().setComponent(new ComponentName("org.shiraz.servicefixture","org.shiraz.servicefixture.Worker")),connection,BIND_AUTO_CREATE))throw new AssertionError("Bind rejected");
 }
 protected void onDestroy(){if(!complete)throw new AssertionError("Incomplete session test");super.onDestroy();}
}
