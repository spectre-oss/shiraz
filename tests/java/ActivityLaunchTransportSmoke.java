// SPDX-License-Identifier: GPL-3.0-only
import android.accounts.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.ActivityLaunchEndpoint;
import java.util.concurrent.*;

/** Launch Intent and callback travel in opposite directions across two ART processes. */
public final class ActivityLaunchTransportSmoke {
 public static void main(String[] args)throws Exception{
  int fd=Integer.parseInt(args[1]);
  if("receive".equals(args[0])){
   ActivityLaunchEndpoint endpoint=new ActivityLaunchEndpoint("test.target","test.target.Login",10000);
   try(BinderTransport transport=new BinderTransport(fd,endpoint,10000)){
    Intent launch=endpoint.await(10,TimeUnit.SECONDS);
    if(!"test.LOGIN".equals(launch.getAction())||!"https://example.invalid/login".equals(launch.getDataString())||launch.getIntArrayExtra("values")[1]!=42)throw new AssertionError("Launch fields lost");
    AccountAuthenticatorResponse response=launch.getParcelableExtra(AccountManager.KEY_ACCOUNT_MANAGER_RESPONSE);
    if(response==null)throw new AssertionError("Callback capability lost");
    Bundle result=new Bundle();result.putString("fixture-result","complete");response.onResult(result);
    if(!transport.awaitClosed(5,TimeUnit.SECONDS))throw new AssertionError("Launch peer did not close");
   }finally{endpoint.close();}
   ActivityLaunchEndpoint timeout=new ActivityLaunchEndpoint("test.target","test.target.Login",10000);
   try{timeout.await(1,TimeUnit.MILLISECONDS);throw new AssertionError("Missing launch did not expire");}catch(java.io.IOException expected){}
   System.out.println("ACTIVITY_LAUNCH_RECEIVER_OK");
  }else{
   final CountDownLatch done=new CountDownLatch(1);final boolean[] valid={false};
   IAccountAuthenticatorResponse.Stub response=new IAccountAuthenticatorResponse.Stub(){
    public void onResult(Bundle result){valid[0]=Binder.getCallingPid()!=android.os.Process.myPid()&&Binder.getCallingUid()==10001&&"complete".equals(result.getString("fixture-result"));done.countDown();}
    public void onRequestContinued(){throw new AssertionError("Unexpected continuation");}
    public void onError(int code,String message){throw new AssertionError("Unexpected error");}
   };
   try(BinderTransport transport=new BinderTransport(fd,null,10001)){
    IBinder root=transport.remoteRoot();
    Intent launch=new Intent("test.LOGIN").setComponent(new ComponentName("test.target","test.target.Login")).setData(android.net.Uri.parse("https://example.invalid/login"));
    launch.putExtra("values",new int[]{7,42});launch.putExtra(AccountManager.KEY_ACCOUNT_MANAGER_RESPONSE,new AccountAuthenticatorResponse(response));
    for(Intent invalid:new Intent[]{null,new Intent(launch).setComponent(new ComponentName("test.other","test.other.Login")),new Intent(launch).setPackage("test.other"),new Intent(launch).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}){
     try{ActivityLaunchEndpoint.send(root,invalid);throw new AssertionError("Invalid launch accepted");}catch(RemoteException|RuntimeException expected){}
    }
    for(boolean wrongInterface:new boolean[]{false,true}){
     Parcel malformed=Parcel.obtain(),reply=Parcel.obtain();
     try{
      malformed.writeInterfaceToken(wrongInterface?"wrong.interface":"shiraz.runtime.IActivityLaunch");malformed.writeTypedObject(launch,0);if(!wrongInterface)malformed.writeInt(9);
      try{root.transact(1,malformed,reply,0);reply.readException();throw new AssertionError("Malformed activity request accepted");}catch(RemoteException|RuntimeException expected){}
     }finally{malformed.recycle();reply.recycle();}
    }
    // A sender's thread-local identity cannot replace the broker-pinned UID.
    long saved=Binder.clearCallingIdentity();
    try{Binder.restoreCallingIdentity(((long)1234<<32)|10002L);ActivityLaunchEndpoint.send(root,launch);}finally{Binder.restoreCallingIdentity(saved);}
    try{ActivityLaunchEndpoint.send(root,launch);throw new AssertionError("Duplicate launch accepted");}catch(RemoteException|RuntimeException expected){}
    if(!done.await(5,TimeUnit.SECONDS)||!valid[0])throw new AssertionError("Remote account callback missing/invalid");
   }
   System.out.println("ACTIVITY_LAUNCH_SENDER_OK");
  }
 }
}
