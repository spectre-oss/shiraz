// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.accounts.*;
import android.content.Intent;
import android.os.*;

public final class AddAccountEndpointSmoke {
 public static void main(String[] args)throws Exception{
  java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();java.io.DataOutputStream wire=new java.io.DataOutputStream(bytes);
  wire.writeInt(4);wire.writeInt(1);wire.writeInt(10000);
  for(String value:new String[]{"test.caller","test.accounts"}){byte[] encoded=value.getBytes("UTF-8");wire.writeInt(encoded.length);wire.write(encoded);}
  byte[] valid=bytes.toByteArray();AddAccountAdmission admitted=AddAccountAdmission.decode(valid);
  if(admitted.uid!=10000||!"test.caller".equals(admitted.caller)||!"test.accounts".equals(admitted.type))throw new AssertionError("Admission metadata");
  for(int size=0;size<valid.length;size++)try{AddAccountAdmission.decode(java.util.Arrays.copyOf(valid,size));throw new AssertionError("Truncated admission accepted");}catch(java.io.IOException expected){}
  for(int offset:new int[]{3,7,8,12}){byte[] bad=valid.clone();bad[offset]=(byte)255;try{AddAccountAdmission.decode(bad);throw new AssertionError("Invalid admission accepted");}catch(java.io.IOException expected){}}
  try{AddAccountAdmission.decode(java.util.Arrays.copyOf(valid,valid.length+1));throw new AssertionError("Trailing admission accepted");}catch(java.io.IOException expected){}
  long identity=Binder.clearCallingIdentity();Binder.restoreCallingIdentity(((long)42<<32)|10000L);
  try{
   final int[] calls={0},results={0},errors={0};final Bundle[] delivered={null};
   final IAccountAuthenticatorResponse[] providerResponse={null};
   Binder provider=new Binder(){
    public String getInterfaceDescriptor(){return "android.accounts.IAccountAuthenticator";}
    protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){
     if(code!=1||!AuthenticatorEndpoint.permits(42,10000))throw new AssertionError("Operation or identity scope");
     data.enforceInterface("android.accounts.IAccountAuthenticator");
     providerResponse[0]=IAccountAuthenticatorResponse.Stub.asInterface(data.readStrongBinder());
     if(!"test.accounts".equals(data.readString()))throw new AssertionError("Account type");
     data.readString();data.createStringArray();Bundle options=data.readTypedObject(Bundle.CREATOR);data.enforceNoDataAvail();
     if(options.getInt(AccountManager.KEY_CALLER_UID)!=10000||options.getInt(AccountManager.KEY_CALLER_PID)!=42||!"test.caller".equals(options.getString(AccountManager.KEY_ANDROID_PACKAGE_NAME)))throw new AssertionError("Forged caller metadata");
     calls[0]++;return true;
    }
   };
   IAccountAuthenticatorResponse callback=new IAccountAuthenticatorResponse.Stub(){
    public void onResult(Bundle result){results[0]++;delivered[0]=result;}
    public void onError(int code,String message){errors[0]++;}
    public void onRequestContinued(){}
   };
   AddAccountEndpoint endpoint=new AddAccountEndpoint(provider,"test.accounts","test.caller","test.owner",10000);
   IAccountAuthenticator client=IAccountAuthenticator.Stub.asInterface(endpoint);
   Binder.restoreCallingIdentity(((long)42<<32)|10001L);
   try{client.addAccount(callback,"test.accounts",null,null,null);throw new AssertionError("Other caller UID allowed");}catch(SecurityException expected){}
   finally{Binder.restoreCallingIdentity(((long)42<<32)|10000L);}
   try{client.getAuthTokenLabel(callback,"token");throw new AssertionError("Other operation allowed");}catch(SecurityException expected){}
   try{client.addAccount(callback,"other.type",null,null,null);throw new AssertionError("Other type allowed");}catch(IllegalArgumentException expected){}
   if(calls[0]!=0)throw new AssertionError("Invalid request reached provider");
   Bundle options=new Bundle();options.putInt(AccountManager.KEY_CALLER_UID,0);options.putString(AccountManager.KEY_ANDROID_PACKAGE_NAME,"forged");
   client.addAccount(callback,"test.accounts",null,null,options);
   if(calls[0]!=1||options.getInt(AccountManager.KEY_CALLER_UID)!=0||AuthenticatorEndpoint.permits(42,10000))throw new AssertionError("Invocation isolation");
   try{client.addAccount(callback,"test.accounts",null,null,null);throw new AssertionError("Repeated request");}catch(SecurityException expected){}
   Bundle ui=new Bundle();ui.putString(AccountManager.KEY_AUTHTOKEN,"synthetic-secret");
   ui.putParcelable(AccountManager.KEY_INTENT,new Intent().setClassName("test.owner","test.owner.Login"));
   providerResponse[0].onResult(ui);
   if(results[0]!=1||delivered[0].containsKey(AccountManager.KEY_AUTHTOKEN)||!ui.containsKey(AccountManager.KEY_AUTHTOKEN))throw new AssertionError("UI result filtering/copy");
   Intent shown=delivered[0].getParcelable(AccountManager.KEY_INTENT);
   AccountAuthenticatorResponse continuation=shown.getParcelableExtra(AccountManager.KEY_ACCOUNT_MANAGER_RESPONSE);
   Bundle done=new Bundle();done.putString(AccountManager.KEY_ACCOUNT_NAME,"fixture");done.putString(AccountManager.KEY_ACCOUNT_TYPE,"test.accounts");done.putString(AccountManager.KEY_AUTHTOKEN,"synthetic-secret");
   continuation.onResult(done);providerResponse[0].onResult(done);
   if(results[0]!=2||delivered[0].containsKey(AccountManager.KEY_AUTHTOKEN)||errors[0]!=0)throw new AssertionError("Terminal filtering or duplicate");
   AddAccountEndpoint revoked=new AddAccountEndpoint(provider,"test.accounts","test.caller","test.owner",10000);
   IAccountAuthenticator.Stub.asInterface(revoked).addAccount(callback,"test.accounts",null,null,null);revoked.close();providerResponse[0].onResult(done);
   if(results[0]!=2)throw new AssertionError("Callback after revocation");
   AddAccountEndpoint foreign=new AddAccountEndpoint(provider,"test.accounts","test.caller","test.owner",10000);
   IAccountAuthenticator.Stub.asInterface(foreign).addAccount(callback,"test.accounts",null,null,null);
   ui.putParcelable(AccountManager.KEY_INTENT,new Intent().setClassName("test.foreign","test.foreign.Login"));providerResponse[0].onResult(ui);
   if(errors[0]!=1||results[0]!=2)throw new AssertionError("Foreign UI accepted");
   AddAccountEndpoint repeatedUi=new AddAccountEndpoint(provider,"test.accounts","test.caller","test.owner",10000);
   IAccountAuthenticator.Stub.asInterface(repeatedUi).addAccount(callback,"test.accounts","",new String[]{""},null);
   ui.putParcelable(AccountManager.KEY_INTENT,new Intent().setClassName("test.owner","test.owner.Login"));
   providerResponse[0].onResult(ui);providerResponse[0].onResult(ui);providerResponse[0].onResult(done);
   if(errors[0]!=2||results[0]!=3)throw new AssertionError("Repeated UI or terminal callback accepted");
   final java.util.ArrayList<android.content.pm.ResolveInfo> matches=new java.util.ArrayList<android.content.pm.ResolveInfo>();
   android.content.pm.PackageManager packages=new android.content.pm.PackageManager(){
    public android.content.pm.ActivityInfo getActivityInfo(android.content.ComponentName c,int f){throw new AssertionError();}
    public android.content.pm.ServiceInfo getServiceInfo(android.content.ComponentName c,int f){throw new AssertionError();}
    public android.content.pm.PackageInfo getPackageInfo(String p,int f){throw new AssertionError();}
    public android.content.pm.ApplicationInfo getApplicationInfo(String p,int f){throw new AssertionError();}
    public CharSequence getApplicationLabel(android.content.pm.ApplicationInfo a){throw new AssertionError();}
    public java.util.List<android.content.pm.ResolveInfo> queryIntentActivities(Intent intent,int flags){
     if(!"test.owner".equals(intent.getPackage())||flags!=MATCH_DEFAULT_ONLY)throw new AssertionError("Unscoped UI query");return matches;
    }
   };
   for(int mode=0;mode<4;mode++){
    matches.clear();android.content.pm.ResolveInfo resolved=new android.content.pm.ResolveInfo();resolved.activityInfo=new android.content.pm.ActivityInfo();
    resolved.activityInfo.packageName=mode==1?"test.foreign":"test.owner";resolved.activityInfo.name="test.owner.Login";matches.add(resolved);
    if(mode==2){android.content.pm.ResolveInfo other=new android.content.pm.ResolveInfo();other.activityInfo=new android.content.pm.ActivityInfo();other.activityInfo.packageName="test.owner";other.activityInfo.name="test.owner.Other";matches.add(other);}
    AddAccountEndpoint implicit=new AddAccountEndpoint(provider,"test.accounts","test.caller","test.owner",10000,packages);
    IAccountAuthenticator.Stub.asInterface(implicit).addAccount(callback,"test.accounts",null,null,null);
    Intent original=new Intent("test.LOGIN");if(mode==3)original.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    ui.putParcelable(AccountManager.KEY_INTENT,original);int beforeErrors=errors[0],beforeResults=results[0];providerResponse[0].onResult(ui);
    if(mode==0){Intent target=delivered[0].getParcelable(AccountManager.KEY_INTENT);if(results[0]!=beforeResults+1||!"test.owner.Login".equals(target.getComponent().getClassName())||original.getComponent()!=null)throw new AssertionError("Owned implicit UI resolution");}
    else if(errors[0]!=beforeErrors+1||results[0]!=beforeResults)throw new AssertionError("Unsafe implicit UI accepted");
    implicit.close();
   }
   System.out.println("ADD_ACCOUNT_ENDPOINT_OK");
  }finally{Binder.restoreCallingIdentity(identity);}
 }
}
