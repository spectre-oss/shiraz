// SPDX-License-Identifier: GPL-3.0-only
import android.accounts.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import shiraz.runtime.ServiceBrokerClient;

/** Trusted test harness supplies the endpoint admitted for a live signed caller. */
public final class SignedActivityLaunchSender {
 public static void main(String[] args)throws Exception{
  int fd=Integer.parseInt(args[0]),uid=Integer.parseInt(args[3]);final String mode=args[4];
  final CountDownLatch done=new CountDownLatch(2);final AtomicInteger continued=new AtomicInteger(),terminal=new AtomicInteger();
  IAccountAuthenticatorResponse.Stub callback=new IAccountAuthenticatorResponse.Stub(){
   private void origin(){if(Binder.getCallingPid()==android.os.Process.myPid()||Binder.getCallingUid()!=uid)throw new AssertionError("Signed activity callback identity");}
   public void onRequestContinued(){origin();continued.incrementAndGet();done.countDown();}
   public void onResult(Bundle result){origin();if(!"result".equals(mode)||!"synthetic-result".equals(result.getString("fixture-result")))throw new AssertionError("Signed activity result");terminal.incrementAndGet();done.countDown();}
   public void onError(int code,String message){origin();if(!"cancel".equals(mode)||code!=AccountManager.ERROR_CODE_CANCELED)throw new AssertionError("Signed activity cancellation");terminal.incrementAndGet();done.countDown();}
  };
  try(ServiceBrokerClient broker=new ServiceBrokerClient(fd);
      ServiceBrokerClient.ActivityConnection connection=broker.openActivity(broker.resolveActivity(args[1],args[2]))){
   if(connection.resolution.uid!=uid)throw new AssertionError("Admitted target UID");
   if(!"disconnect".equals(mode)){
   Intent launch=new Intent("test.SIGNED_LOGIN").setComponent(new ComponentName(args[1],args[2])).setData(android.net.Uri.parse("https://example.invalid/login"));
   launch.addCategory("test.CATEGORY");launch.putExtra("fixture-values",new int[]{7,42});launch.putExtra("fixture-mode",mode);
   launch.putExtra(AccountManager.KEY_ACCOUNT_MANAGER_RESPONSE,new AccountAuthenticatorResponse(callback));
   connection.send(launch);
   if(!done.await(10,TimeUnit.SECONDS)||continued.get()!=1||terminal.get()!=1)throw new AssertionError("Signed callback lifecycle incomplete");
   }
   connection.close();connection.close();
   if(!broker.resolveActivity(args[1],args[2]).component.equals(connection.resolution.component))throw new AssertionError("Activity release broke broker");
  }
  System.out.println("SIGNED_ACTIVITY_CALLBACK_OK mode="+mode);
 }
}
