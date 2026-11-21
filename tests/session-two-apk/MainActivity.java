// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.sessionfixture;
import android.app.Activity;
import android.content.*;
import android.os.*;
public final class MainActivity extends Activity {
 private boolean complete,tailStarted,dynamicReceived;
 static MainActivity current;
 static boolean liveReceived;
 private BroadcastReceiver dynamic,secondaryDynamic;private int dynamicCount,secondaryCount;
 static volatile boolean destroyed;
 protected void onCreate(Bundle state){
  super.onCreate(state);current=this;
  Intent launcher=getPackageManager().getLaunchIntentForPackage(getPackageName());
  if(launcher==null||!"org.shiraz.sessiontwo.Launcher".equals(launcher.getComponent().getClassName()))throw new AssertionError("Alias launch component");
  try{
   android.content.pm.ActivityInfo[] activities=getPackageManager().getPackageInfo(getPackageName(),android.content.pm.PackageManager.GET_ACTIVITIES).activities;
   if(activities==null||activities.length!=4)throw new AssertionError("Package activity alias enumeration");
   int aliases=0;for(android.content.pm.ActivityInfo info:activities)if(info.targetActivity!=null)aliases++;
   if(aliases!=2)throw new AssertionError("Package alias target metadata");
  }catch(android.content.pm.PackageManager.NameNotFoundException error){throw new AssertionError(error);}
  System.out.println("SESSION_ALIAS_DISCOVERY_OK");android.widget.TextView view=new android.widget.TextView(this);view.setText("Shared host session fixture");setContentView(view);
  sendBroadcast(new Intent().setComponent(new ComponentName(getPackageName(),getPackageName()+".DisabledReceiver")));
  Intent remote=new Intent("org.shiraz.SECONDARY_BROADCAST").setComponent(new ComponentName(getPackageName(),"org.shiraz.sessionfixture.RemoteReceiver"));
  remote.putExtra("pid",android.os.Process.myPid()).putExtra("uid",android.os.Process.myUid()).putExtra("payload","secondary-snapshot");
  sendBroadcast(new Intent(remote).addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY));
  sendBroadcast(remote,"test.MISSING");
  sendBroadcast(remote,"org.shiraz.sessiontwo.RECEIVE");
  Intent implicit=new Intent(remote).setComponent(null).setPackage(getPackageName()).addCategory("org.shiraz.SECONDARY_CATEGORY").putExtra("implicit",true);
  if(getPackageManager().queryBroadcastReceivers(implicit,0).size()!=2)throw new AssertionError("Secondary filter discovery");
  secondaryDynamic=new BroadcastReceiver(){public void onReceive(Context context,Intent value){if(value.getComponent()!=null)throw new AssertionError("Implicit intent rewritten");secondaryCount++;}};
  IntentFilter secondaryFilter=new IntentFilter("org.shiraz.SECONDARY_BROADCAST");secondaryFilter.addCategory("org.shiraz.SECONDARY_CATEGORY");registerReceiver(secondaryDynamic,secondaryFilter);
  sendBroadcast(new Intent(implicit).addCategory("org.shiraz.UNKNOWN"));
  sendBroadcast(implicit,"test.MISSING");
  sendBroadcast(implicit,"org.shiraz.sessiontwo.RECEIVE");
  sendBroadcast(new Intent(implicit).addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY));
  remote.putExtra("payload","mutated");
  Intent broadcast=new Intent("org.shiraz.sessiontwo.BROADCAST").setPackage(getPackageName()).addCategory("org.shiraz.sessiontwo.TEST").putExtra("payload","local-manifest");
  if(getPackageManager().queryBroadcastReceivers(broadcast,0).size()!=1)throw new AssertionError("Manifest filter discovery");
  if(!getPackageManager().queryBroadcastReceivers(new Intent(broadcast).addCategory("test.UNKNOWN"),0).isEmpty())throw new AssertionError("Manifest category mismatch");
  dynamic=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){dynamicCount++;}};
  IntentFilter dynamicFilter=new IntentFilter("org.shiraz.sessiontwo.BROADCAST");dynamicFilter.addCategory("org.shiraz.sessiontwo.TEST");registerReceiver(dynamic,dynamicFilter,"org.shiraz.sessiontwo.RECEIVE",null);
  registerReceiver(dynamic,new IntentFilter("org.shiraz.sessiontwo.DENIED"),"test.MISSING",null);
  sendBroadcast(new Intent("org.shiraz.sessiontwo.DENIED").setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY));
  sendBroadcast(broadcast,"test.MISSING");
  sendBroadcast(broadcast,"org.shiraz.sessiontwo.RECEIVE");
  sendBroadcast(new Intent(broadcast).addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY));
  BroadcastReceiver remoteDynamic=new BroadcastReceiver(){public void onReceive(Context context,Intent value){
   if(dynamicReceived||destroyed||Looper.myLooper()!=Looper.getMainLooper()||value.getComponent()!=null||!"dynamic-snapshot".equals(value.getStringExtra("payload")))throw new AssertionError("Remote dynamic callback identity or snapshot");
   dynamicReceived=true;final PendingResult pending=goAsync();
   new Handler(Looper.getMainLooper()).postDelayed(new Runnable(){public void run(){System.out.println("SESSION_DYNAMIC_RECEIVER_FINISHED "+android.os.Process.myPid());pending.finish();}},250);
   finishAfterLive();
  }};
  IntentFilter crossFilter=new IntentFilter("org.shiraz.DYNAMIC_BROADCAST");crossFilter.addCategory("org.shiraz.DYNAMIC_CATEGORY");
  registerReceiver(remoteDynamic,crossFilter,"org.shiraz.sessiontwo.RECEIVE",null,Context.RECEIVER_EXPORTED);
  crossFilter.setPriority(5);registerReceiver(remoteDynamic,crossFilter,"org.shiraz.sessiontwo.RECEIVE",null,Context.RECEIVER_EXPORTED);
  BroadcastReceiver privateDynamic=new BroadcastReceiver(){public void onReceive(Context context,Intent value){throw new AssertionError("Private dynamic receiver reached");}};
  registerReceiver(privateDynamic,crossFilter,null,null,Context.RECEIVER_NOT_EXPORTED);
  BroadcastReceiver removedDynamic=new BroadcastReceiver(){public void onReceive(Context context,Intent value){throw new AssertionError("Removed dynamic receiver reached");}};
  registerReceiver(removedDynamic,crossFilter);unregisterReceiver(removedDynamic);
  startActivityForResult(new Intent("org.shiraz.sessiontwo.DETAIL").setPackage(getPackageName()),73);
 }
 protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=73||result!=RESULT_OK||data==null||!data.getBooleanExtra("alias",false))throw new AssertionError("Alias activity result");
  if(secondaryCount!=2)throw new AssertionError("Secondary implicit fanout");unregisterReceiver(secondaryDynamic);
  if(dynamicCount!=2)throw new AssertionError("Dynamic and manifest fanout");unregisterReceiver(dynamic);
  if(ManifestReceiver.delivered!=1)throw new AssertionError("Manifest broadcast delivery count");
  System.out.println("SESSION_ALIAS_LIFECYCLE_OK");
  ServiceConnection connection=new ServiceConnection(){
   public void onServiceConnected(ComponentName name,IBinder binder){
    Parcel input=Parcel.obtain(),output=Parcel.obtain();
    try{
     input.writeInterfaceToken("shiraz.fixture.service");if(!binder.transact(6,input,output,0))throw new AssertionError("Barrier method");output.readException();
     int pid=output.readInt(),instance=output.readInt(),callers=output.readInt(),uid=output.readInt();output.enforceNoDataAvail();
     if(callers!=2||uid!=android.os.Process.myUid())throw new AssertionError("Session caller identity");
     System.out.println("SESSION_SHARED_RESULT "+getPackageName()+" "+pid+" "+instance+" "+uid);complete=true;
    }catch(RemoteException error){throw new AssertionError(error);}finally{input.recycle();output.recycle();unbindService(this);}
    finishAfterLive();
   }
   public void onServiceDisconnected(ComponentName name){throw new AssertionError("Disconnected");}
   public void onBindingDied(ComponentName name){throw new AssertionError("Binding died");}
  };
  if(!bindService(new Intent().setComponent(new ComponentName("org.shiraz.servicefixture","org.shiraz.servicefixture.Worker")),connection,BIND_AUTO_CREATE))throw new AssertionError("Bind rejected");
 }
 void finishAfterLive(){if(complete&&liveReceived&&dynamicReceived&&!tailStarted){tailStarted=true;sendBroadcast(new Intent().setComponent(new ComponentName(getPackageName(),"org.shiraz.sessionfixture.TailReceiver")));finish();}}
 protected void onDestroy(){destroyed=true;if(!complete||!dynamicReceived)throw new AssertionError("Incomplete session test");super.onDestroy();}
}
