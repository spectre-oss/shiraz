// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.accounts.*;
import android.os.*;
public final class AccountListenerAcceptance {
 public static void run(final android.app.Activity activity){
  final AccountManager manager=AccountManager.get(activity);
  final Account account=new Account("signed-listener-user","org.shiraz.fixture.account");
  final Handler handler=new Handler(Looper.getMainLooper());
  final int[] stage={0},completed={0};
  final boolean[] done={false};
  final OnAccountsUpdateListener[] listener=new OnAccountsUpdateListener[1];
  final OnAccountsUpdateListener unrelated=new OnAccountsUpdateListener(){public void onAccountsUpdated(Account[] accounts){throw new AssertionError("Unrelated account type notified");}};
  final Runnable finishTest=new Runnable(){public void run(){
   if(stage[0]==3&&completed[0]==2&&!done[0]){
    done[0]=true;manager.removeOnAccountsUpdatedListener(listener[0]);manager.removeOnAccountsUpdatedListener(unrelated);
    System.out.println("SIGNED_ACCOUNT_LISTENERS_OK package="+activity.getPackageName()+" pid="+android.os.Process.myPid());activity.finish();
   }
  }};
  listener[0]=new OnAccountsUpdateListener(){public void onAccountsUpdated(Account[] accounts){
   if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Signed listener callback thread");
   int current=stage[0]++;
   if(current==1){if(accounts.length!=1||!account.equals(accounts[0]))throw new AssertionError("Signed account add notice");}
   else if((current==0||current==2)&&accounts.length!=0)throw new AssertionError("Signed empty snapshot");
   else if(current>2)throw new AssertionError("Duplicate signed notification");
   if(current==2){finishTest.run();return;}
   manager.hasFeatures(account,new String[]{current==0?"listener-add":"listener-remove"},new AccountManagerCallback<Boolean>(){public void run(AccountManagerFuture<Boolean> future){
    try{if(!future.getResult())throw new AssertionError("Provider mutation failed");}catch(Exception failure){throw new AssertionError(failure);}
    completed[0]++;finishTest.run();
   }},null);
  }};
  manager.addOnAccountsUpdatedListener(unrelated,null,false,new String[]{"org.shiraz.fixture.custom"});
  manager.addOnAccountsUpdatedListener(listener[0],null,true,new String[]{account.type});
  handler.postDelayed(new Runnable(){public void run(){if(!done[0])throw new AssertionError("Signed account listener timeout stage="+stage[0]);}},15000);
 }
}
