// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.brokercaller;
import android.app.Activity;
import android.content.*;
import android.os.*;
public final class CallerActivity extends Activity {
 private int connected;private IBinder firstBinder;private boolean complete;
 private final ServiceConnection first=new Connection(),second=new Connection();
 private final class Connection implements ServiceConnection {
  public void onServiceConnected(ComponentName name,IBinder binder){
   if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Callback thread");
   if(!"org.shiraz.servicefixture".equals(name.getPackageName()))throw new AssertionError("Component identity");
   if(connected==0)firstBinder=binder;else if(firstBinder!=binder)throw new AssertionError("Duplicate service binding");
   Parcel input=Parcel.obtain(),output=Parcel.obtain();
   try{
    input.writeInterfaceToken("shiraz.fixture.service");input.writeInt(41);
    if(!binder.transact(1,input,output,0))throw new AssertionError("Unhandled method");
    output.readException();if(output.readInt()!=42||!"org.shiraz.servicefixture".equals(output.readString()))throw new AssertionError("Target response");
    output.enforceNoDataAvail();
   }catch(RemoteException error){throw new AssertionError(error);}finally{input.recycle();output.recycle();}
   verifyIdentity(binder);
   System.out.println("CONTEXT_BROKER_CONNECTED_"+(++connected));
   if(connected==2){unbindService(first);unbindService(second);complete=true;System.out.println("CONTEXT_BROKER_UNBOUND");finish();}
  }
  public void onServiceDisconnected(ComponentName name){throw new AssertionError("Unexpected disconnect");}
  public void onBindingDied(ComponentName name){throw new AssertionError("Binding died");}
  public void onNullBinding(ComponentName name){throw new AssertionError("Null binding");}
 }
 private void verifyIdentity(IBinder remote){
  final int own=android.os.Process.myUid();final int[] callbackUid={-1};
  if(own<10000||own>19999||getApplicationInfo().uid!=own)throw new AssertionError("Caller UID");
  Binder callback=new Binder(){protected boolean onTransact(int code,Parcel input,Parcel output,int flags){
   input.enforceNoDataAvail();callbackUid[0]=Binder.getCallingUid();
   long token=Binder.clearCallingIdentity();try{if(Binder.getCallingUid()!=own)throw new AssertionError("Callback identity clear");}finally{Binder.restoreCallingIdentity(token);}
   if(Binder.getCallingUid()!=callbackUid[0])throw new AssertionError("Callback identity restore");
   output.writeNoException();output.writeInt(android.os.Process.myUid());return true;
  }};
  Parcel input=Parcel.obtain(),output=Parcel.obtain();
  try{
   input.writeInterfaceToken("shiraz.fixture.service");input.writeStrongBinder(callback);
   if(!remote.transact(2,input,output,0))throw new AssertionError("Identity method");output.readException();
   int caller=output.readInt(),target=output.readInt(),cleared=output.readInt(),restored=output.readInt(),callbackOwn=output.readInt();String callerName=output.readString();output.enforceNoDataAvail();
   if(!getPackageName().equals(callerName)||!"org.shiraz.servicefixture".equals(getPackageManager().getNameForUid(target)))throw new AssertionError("UID package lookup");
   if(getPackageManager().getPackagesForUid(0)!=null||getPackageManager().getPackagesForUid(-1)!=null)throw new AssertionError("Invented system package");
   try{getPackageManager().getPackageUid("org.shiraz.notinstalled",0);throw new AssertionError("Invented package UID");}catch(android.content.pm.PackageManager.NameNotFoundException expected){}
   if(caller!=own||target==own||target<10000||target>19999||cleared!=target||restored!=own||callbackOwn!=own||callbackUid[0]!=target)throw new AssertionError("Cross-package Binder identity");
   android.content.pm.PackageManager pm=getPackageManager();
   if(pm.checkSignatures(own,own)!=pm.SIGNATURE_MATCH||pm.checkSignatures(getPackageName(),getPackageName())!=pm.SIGNATURE_MATCH)throw new AssertionError("Self signature match");
   if(pm.checkSignatures(own,target)!=pm.SIGNATURE_NO_MATCH||pm.checkSignatures(getPackageName(),"org.shiraz.servicefixture")!=pm.SIGNATURE_NO_MATCH)throw new AssertionError("Different signer accepted");
   if(pm.checkSignatures("org.shiraz.servicefixture","org.shiraz.backgroundfixture")!=pm.SIGNATURE_MATCH)throw new AssertionError("Same signer rejected");
   if(pm.checkSignatures(0,own)!=pm.SIGNATURE_UNKNOWN_PACKAGE||pm.checkSignatures("org.shiraz.notinstalled",getPackageName())!=pm.SIGNATURE_UNKNOWN_PACKAGE)throw new AssertionError("Unknown signer invented");
   System.out.println("CONTEXT_BROKER_SIGNATURES_OK");
   if(Binder.getCallingUid()!=own)throw new AssertionError("Caller identity leaked to main");
   System.out.println("CONTEXT_BROKER_CALLER_UID="+own);System.out.println("CONTEXT_BROKER_TARGET_UID="+target);System.out.println("CONTEXT_BROKER_IDENTITIES_OK");
  }catch(RemoteException error){throw new AssertionError(error);}finally{input.recycle();output.recycle();}
 }
 protected void onCreate(Bundle state){
  super.onCreate(state);
  android.widget.TextView status=new android.widget.TextView(this);status.setText("Testing registered service binding");setContentView(status);
  Intent target=new Intent("org.shiraz.servicefixture.TYPED").setPackage("org.shiraz.servicefixture").addCategory("org.shiraz.servicefixture.CLIENT").setDataAndType(android.net.Uri.parse("content://fixture/item"),"text/plain");
  java.util.List<android.content.pm.ResolveInfo> discovered=getPackageManager().queryIntentServices(target,android.content.pm.PackageManager.GET_RESOLVED_FILTER);
  if(discovered.size()!=1||!discovered.get(0).serviceInfo.name.equals("org.shiraz.servicefixture.Worker")||!discovered.get(0).filter.hasDataType("text/plain"))throw new AssertionError("Registered service query");
  Intent global=new Intent(target).setPackage(null);
  java.util.List<android.content.pm.ResolveInfo> globalServices=getPackageManager().queryIntentServices(global,android.content.pm.PackageManager.GET_RESOLVED_FILTER);
  if(globalServices.size()!=1||!globalServices.get(0).serviceInfo.name.equals("org.shiraz.servicefixture.Worker")||!globalServices.get(0).filter.hasDataType("text/plain"))throw new AssertionError("Global registered service query");
  if(global.getPackage()!=null||global.getComponent()!=null)throw new AssertionError("Service discovery mutated caller intent");
  if(!getPackageManager().queryIntentServices(new Intent(global).addCategory("unsupported"),0).isEmpty())throw new AssertionError("Global service category mismatch ignored");
  if(!getPackageManager().queryIntentServices(new Intent(global).setAction("org.shiraz.NO_SUCH_SERVICE"),0).isEmpty())throw new AssertionError("Global absent service returned");
  System.out.println("REGISTERED_GLOBAL_SERVICE_QUERY_OK");
  try{
   android.content.pm.ServiceInfo info=getPackageManager().getServiceInfo(new ComponentName("org.shiraz.servicefixture","org.shiraz.servicefixture.Worker"),0);
   if(info.applicationInfo.uid!=getPackageManager().getPackageUid("org.shiraz.servicefixture",0)||!info.processName.equals("org.shiraz.servicefixture:worker"))throw new AssertionError("Registered service identity");
   android.content.pm.PackageInfo installed=getPackageManager().getPackageInfo("org.shiraz.servicefixture",android.content.pm.PackageManager.GET_SERVICES|android.content.pm.PackageManager.GET_SIGNATURES);
   android.content.pm.ApplicationInfo application=getPackageManager().getApplicationInfo("org.shiraz.servicefixture",0);
   if(installed.versionCode!=1||installed.services==null||installed.services.length!=6||installed.signatures==null||installed.signatures.length!=1)throw new AssertionError("Registered package facts version="+installed.versionCode+" services="+(installed.services==null?-1:installed.services.length)+" certificates="+(installed.signatures==null?-1:installed.signatures.length));
   android.content.pm.Signature[] own=getPackageManager().getPackageInfo(getPackageName(),android.content.pm.PackageManager.GET_SIGNATURES).signatures;
   android.content.pm.Signature[] matching=getPackageManager().getPackageInfo("org.shiraz.backgroundfixture",android.content.pm.PackageManager.GET_SIGNATURES).signatures;
   if(own==null||own.length!=1||matching==null||matching.length!=1||own[0].equals(installed.signatures[0])||!matching[0].equals(installed.signatures[0]))throw new AssertionError("Enrolled signing certificate mismatch");
   if(application.uid!=info.applicationInfo.uid||!application.sourceDir.equals(installed.applicationInfo.sourceDir)||!new java.io.File(application.sourceDir).isFile())throw new AssertionError("Registered application identity");
   try{getPackageManager().getPackageInfo("org.shiraz.absent",0);throw new AssertionError("Absent package returned");}catch(android.content.pm.PackageManager.NameNotFoundException expected){}
   try{getPackageManager().getApplicationInfo("org.shiraz.absent",0);throw new AssertionError("Absent application returned");}catch(android.content.pm.PackageManager.NameNotFoundException expected){}
  }catch(android.content.pm.PackageManager.NameNotFoundException error){throw new AssertionError(error);}
  if(!getPackageManager().queryIntentServices(new Intent(target).setPackage("org.shiraz.absent"),0).isEmpty())throw new AssertionError("Missing service query");
  System.out.println("REGISTERED_SERVICE_QUERY_OK");
  java.util.HashSet<String> packages=new java.util.HashSet<String>();
  for(android.content.pm.PackageInfo pkg:getPackageManager().getInstalledPackages(0)){
   if(!packages.add(pkg.packageName))throw new AssertionError("Duplicate installed package");
  }
  if(!packages.contains(getPackageName())||!packages.contains("org.shiraz.servicefixture")||!packages.contains("org.shiraz.backgroundfixture"))throw new AssertionError("Registry packages missing from enumeration: "+packages);
  java.util.HashSet<String> applications=new java.util.HashSet<String>();
  for(android.content.pm.ApplicationInfo app:getPackageManager().getInstalledApplications(0))applications.add(app.packageName);
  if(!applications.equals(packages))throw new AssertionError("Installed applications disagree with packages");
  System.out.println("REGISTERED_PACKAGE_ENUMERATION_OK");
  Intent launch=getPackageManager().getLaunchIntentForPackage("org.shiraz.backgroundfixture");
  if(launch==null||launch.getComponent()==null||!launch.getComponent().equals(new ComponentName("org.shiraz.backgroundfixture","org.shiraz.backgroundfixture.BackgroundActivity"))||!Intent.ACTION_MAIN.equals(launch.getAction())||!launch.hasCategory(Intent.CATEGORY_LAUNCHER)||(launch.getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK)==0)throw new AssertionError("Enrolled launch intent missing or incorrect");
  Intent coexist=getPackageManager().getLaunchIntentForPackage("org.shiraz.servicefixture");
  if(coexist==null||!new ComponentName("org.shiraz.servicefixture","org.shiraz.servicefixture.CoexistActivity").equals(coexist.getComponent()))throw new AssertionError("Enrolled coexist activity missing");
  if(getPackageManager().getLaunchIntentForPackage("org.shiraz.absent")!=null)throw new AssertionError("Absent package invented launcher");
  System.out.println("REGISTERED_LAUNCH_INTENT_OK");
  android.os.PowerManager.WakeLock deniedWake=getSystemService(android.os.PowerManager.class).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"undeclared-caller");
  try{deniedWake.acquire();throw new AssertionError("Undeclared APK wake lock acquired");}catch(SecurityException expected){}
  if(deniedWake.isHeld())throw new AssertionError("Denied APK wake lock retained state");
  for(Intent unmatched:new Intent[]{new Intent(target).addCategory("unsupported"),new Intent(target).setDataAndType(target.getData(),"image/png"),new Intent(target).setDataAndType(android.net.Uri.parse("https://fixture/item"),"text/plain")}){
   if(startService(unmatched)!=null)throw new AssertionError("Nonmatching service filter started a service");
  }
  ServiceConnection cancelled=new ServiceConnection(){
   public void onServiceConnected(ComponentName name,IBinder binder){throw new AssertionError("Cancelled callback");}
   public void onServiceDisconnected(ComponentName name){throw new AssertionError("Cancelled disconnect");}
  };
  try{bindService(new Intent().setComponent(new ComponentName("org.shiraz.servicefixture","org.shiraz.servicefixture.Private")),cancelled,BIND_AUTO_CREATE);throw new AssertionError("Private target accepted");}
  catch(SecurityException expected){System.out.println("CONTEXT_BROKER_PRIVATE_DENIED");}
  try{bindService(target,cancelled,0);throw new AssertionError("Unsupported no-auto-create accepted");}
  catch(UnsupportedOperationException expected){}
  if(!bindService(target,cancelled,BIND_AUTO_CREATE))throw new AssertionError("Cancelled bind rejected");
  unbindService(cancelled);
  if(!bindService(target,first,BIND_AUTO_CREATE)||!bindService(target,second,BIND_AUTO_CREATE))throw new AssertionError("Bind rejected");
  if(target.getComponent()!=null)throw new AssertionError("Action binding mutated caller intent");
  System.out.println("CONTEXT_PACKAGE_ACTION_BIND_OK");
 }
 protected void onDestroy(){if(!complete)throw new AssertionError("Incomplete service flow");System.out.println("CONTEXT_BROKER_CALLER_DESTROYED");super.onDestroy();}
}
