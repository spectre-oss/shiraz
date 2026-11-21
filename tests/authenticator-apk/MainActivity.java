// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.authenticatorfixture;
import android.accounts.*;
import android.os.*;
public final class MainActivity extends android.app.Activity {
 protected void onCreate(Bundle state){
  super.onCreate(state);
  android.widget.TextView status=new android.widget.TextView(this);status.setText("Authenticator fixture");setContentView(status);
  if("1".equals(System.getenv("SHIRAZ_TEST_ACCOUNT_LISTENERS"))){org.shiraz.fixture.AccountListenerAcceptance.run(this);return;}
  String localMode=System.getenv("SHIRAZ_TEST_LOCAL_ACCOUNT_UI");
  if(localMode!=null){verifyLocalUi(localMode);return;}
  AuthenticatorDescription[] values=AccountManager.get(this).getAuthenticatorTypes();
  if(values.length!=2)throw new AssertionError("Authenticator count");
  AuthenticatorDescription value=null;boolean customFound=false;
  for(AuthenticatorDescription item:values){if("org.shiraz.fixture.account".equals(item.type))value=item;else if("org.shiraz.fixture.custom".equals(item.type)&&item.customTokens)customFound=true;}
  if(value==null||!customFound)throw new AssertionError("Authenticator modes missing");
  if(!"org.shiraz.fixture.account".equals(value.type)||!getPackageName().equals(value.packageName)||value.customTokens||value.labelId==0||value.iconId!=0)throw new AssertionError("Authenticator descriptor");
  AuthenticatorDescription key=AuthenticatorDescription.newKey(value.type);
  if(!key.equals(value)||key.hashCode()!=value.hashCode()||key.packageName!=null)throw new AssertionError("Authenticator key equality");
  Parcel parcel=Parcel.obtain();try{
   value.writeToParcel(parcel,0);parcel.setDataPosition(0);AuthenticatorDescription restored=AuthenticatorDescription.CREATOR.createFromParcel(parcel);parcel.enforceNoDataAvail();
   if(!restored.equals(value)||!restored.packageName.equals(value.packageName)||restored.labelId!=value.labelId||restored.customTokens)throw new AssertionError("Authenticator parcel");
  }finally{parcel.recycle();}
  AccountManager manager=AccountManager.get(this);Account account=new Account("fixture-user",value.type);
  if(manager.addAccountExplicitly(account,"fixture-pw",null)){
   manager.setUserData(account,"fixture-stage","persisted");manager.setAuthToken(account,"fixture-token-type","fixture-token");
   System.out.println("AUTHENTICATOR_SHARED_ACCOUNT_CREATED");
  }else{
   if(!"fixture-pw".equals(manager.getPassword(account))||!"persisted".equals(manager.getUserData(account,"fixture-stage"))||!"fixture-token".equals(manager.peekAuthToken(account,"fixture-token-type")))throw new AssertionError("Shared account persistence");
   Account[] accounts=manager.getAccountsByType(value.type);if(accounts.length!=1||!accounts[0].equals(account)||manager.getAccounts().length!=1)throw new AssertionError("Shared account enumeration");
   manager.invalidateAuthToken(value.type,"fixture-token");if(manager.peekAuthToken(account,"fixture-token-type")!=null)throw new AssertionError("Token invalidation");
   manager.setAuthToken(account,"fixture-token-type","another-fixture-token");manager.clearPassword(account);
   if(manager.getPassword(account)!=null||manager.peekAuthToken(account,"fixture-token-type")!=null)throw new AssertionError("Password token clearing");
   if(!manager.removeAccountExplicitly(account)||manager.getAccounts().length!=0)throw new AssertionError("Account removal");
   System.out.println("AUTHENTICATOR_SHARED_ACCOUNT_RESTORED_AND_REMOVED");
  }
  try{verifyAuthenticator();}catch(Exception failure){throw new AssertionError(failure);}
  System.out.println("AUTHENTICATOR_ACCOUNT_MANAGER_DISCOVERY_OK");verifyToken(0);
 }
 private void verifyLocalUi(final String mode){
  Bundle options=new Bundle();options.putString("fixture-option","original");
  AccountManager.get(this).addAccount("org.shiraz.fixture.account","ui-local-"+mode,new String[]{"fixture-feature"},options,this,new AccountManagerCallback<Bundle>(){
   public void run(AccountManagerFuture<Bundle> future){
    if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Local UI callback thread");
    try{
     Bundle result=future.getResult();
     if(!"result".equals(mode)||!"ui-fixture".equals(result.getString(AccountManager.KEY_ACCOUNT_NAME))||!"org.shiraz.fixture.account".equals(result.getString(AccountManager.KEY_ACCOUNT_TYPE)))throw new AssertionError("Local account UI result");
    }catch(android.accounts.OperationCanceledException expected){if(!"cancel".equals(mode))throw new AssertionError(expected);}
    catch(Exception failure){throw new AssertionError(failure);}
    System.out.println("ACCOUNT_LOCAL_UI_CALLER_OK mode="+mode);finish();
   }
  },null);
 }
 private void verifyAuthenticator()throws Exception{
  if(checkSelfPermission("android.permission.ACCOUNT_MANAGER")==0)throw new AssertionError("Caller gained account manager permission");
  final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
  IBinder callback=new Binder(){protected boolean onTransact(int code,Parcel input,Parcel output,int flags){
   input.enforceInterface("android.accounts.IAccountAuthenticatorResponse");
   if(code!=1||input.readInt()!=1)throw new AssertionError("Authenticator callback type");
   Bundle result=Bundle.CREATOR.createFromParcel(input);
   if(!"fixture-label".equals(result.getString(AccountManager.KEY_AUTH_TOKEN_LABEL)))throw new AssertionError("Authenticator label");
   done.countDown();return true;
  }};
  Object connection=Class.forName("shiraz.runtime.HostAccounts").getMethod("open",String.class).invoke(null,"org.shiraz.fixture.account");
  try{
   IBinder binder=(IBinder)connection.getClass().getField("binder").get(connection);
   Parcel request=Parcel.obtain();try{
    request.writeInterfaceToken("android.accounts.IAccountAuthenticator");request.writeStrongBinder(callback);request.writeString("fixture-token");
    if(!binder.transact(4,request,null,IBinder.FLAG_ONEWAY))throw new AssertionError("Authenticator request rejected");
   }finally{request.recycle();}
   if(!done.await(8,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Authenticator callback timeout");
  }finally{((java.io.Closeable)connection).close();}
  if(checkSelfPermission("android.permission.ACCOUNT_MANAGER")==0)throw new AssertionError("Permission escaped session");
  System.out.println("AUTHENTICATOR_SIGNED_SERVICE_REQUEST_OK");
 }
 private void verifyPublicRequest(final int stage){
  String[] names={"fixture-feature","malformed","network","pending","die","fixture-feature","pending"};
  final long started=System.nanoTime();
  String[] features={names[stage]};
  AccountManagerFuture<Boolean> future=AccountManager.get(this).hasFeatures(new Account("fixture-user","org.shiraz.fixture.account"),features,new AccountManagerCallback<Boolean>(){
   public void run(AccountManagerFuture<Boolean> result){
    if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Public account callback is not on main");
    try{
     if(stage==0||stage==5){if(!result.getResult())throw new AssertionError("Public feature result");}
     else if(stage==1){try{result.getResult();throw new AssertionError("Malformed result accepted");}catch(AuthenticatorException expected){}}
     else if(stage==2){try{result.getResult();throw new AssertionError("Network failure missing");}catch(java.io.IOException expected){if(expected.getMessage().contains("fixture-network-detail"))throw new AssertionError("Provider detail exposed");}}
     else if(stage==4){try{result.getResult();throw new AssertionError("Dead worker returned result");}catch(AuthenticatorException expected){}System.out.println("AUTHENTICATOR_WORKER_DEATH_OK");}
     else {try{result.getResult();throw new AssertionError("Canceled result accepted");}catch(android.accounts.OperationCanceledException expected){}if(!result.isCancelled())throw new AssertionError("Cancellation state");
      if(stage==6){long elapsed=(System.nanoTime()-started)/1000000;if(elapsed<29000||elapsed>45000)throw new AssertionError("Session deadline timing: "+elapsed);System.out.println("AUTHENTICATOR_DEADLINE_OK elapsed_ms="+elapsed);}
     }
    }catch(Exception failure){throw new AssertionError(failure);}
    if(stage<6)verifyPublicRequest(stage+1);else verifyCapacity();
   }
  },null);
  features[0]="changed-after-request";
  if(stage==3&&!future.cancel(true))throw new AssertionError("Immediate cancellation lost");
 }
 private void verifyCapacity(){
  final AccountManager manager=AccountManager.get(this);final Account account=new Account("fixture-user","org.shiraz.fixture.account");
  final AccountManagerFuture<?>[] pending=new AccountManagerFuture<?>[4];final int[] canceled={0};
  AccountManagerCallback<Boolean> callback=new AccountManagerCallback<Boolean>(){public void run(AccountManagerFuture<Boolean> result){
   try{result.getResult();throw new AssertionError("Pending request completed");}catch(android.accounts.OperationCanceledException expected){}catch(Exception failure){throw new AssertionError(failure);}
   if(++canceled[0]==4){
    manager.hasFeatures(account,new String[]{"fixture-feature"},new AccountManagerCallback<Boolean>(){public void run(AccountManagerFuture<Boolean> recovered){
     try{if(!recovered.getResult())throw new AssertionError("Capacity not recovered");}catch(Exception failure){throw new AssertionError(failure);}
     System.out.println("AUTHENTICATOR_CAPACITY_RECOVERY_OK");System.out.println("AUTHENTICATOR_PUBLIC_ASYNC_OK");finish();
    }},null);
   }
  }};
  for(int i=0;i<4;i++)pending[i]=manager.hasFeatures(account,new String[]{"pending"},callback,null);
  manager.hasFeatures(account,new String[]{"fixture-feature"},new AccountManagerCallback<Boolean>(){public void run(AccountManagerFuture<Boolean> overflow){
   try{overflow.getResult();throw new AssertionError("Capacity limit bypassed");}catch(AuthenticatorException expected){}catch(Exception failure){throw new AssertionError(failure);}
   for(AccountManagerFuture<?> future:pending)if(!future.cancel(true))throw new AssertionError("Pending request already completed");
  }},null);
 }
 private void verifyToken(final int stage){
  String[] types={"fixture-token","wrong-account","missing","wrong-type","ui","expiry"};
  Bundle options=new Bundle();options.putString("fixture-option","original");options.putString("androidPackageName","forged.package");options.putInt("callerUid",0);options.putInt("callerPid",0);
  AccountManager.get(this).getAuthToken(new Account("fixture-user","org.shiraz.fixture.account"),types[stage],options,false,new AccountManagerCallback<Bundle>(){public void run(AccountManagerFuture<Bundle> result){
   if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Token callback thread");
   try{
    if(stage==0){Bundle value=result.getResult();if(!"synthetic-fixture-token".equals(value.getString(AccountManager.KEY_AUTHTOKEN))||!"fixture-user".equals(value.getString(AccountManager.KEY_ACCOUNT_NAME)))throw new AssertionError("Token result");}
    else {try{result.getResult();throw new AssertionError("Invalid token result accepted");}catch(AuthenticatorException expected){}}
   }catch(Exception failure){throw new AssertionError(failure);}
   if(stage<5)verifyToken(stage+1);else{System.out.println("AUTHENTICATOR_PUBLIC_TOKEN_OK");verifyCache(0,null);}
  }},null);
  options.putString("fixture-option","changed");
 }
 private void verifyCache(final int stage,final String previous){
  final AccountManager manager=AccountManager.get(this);final Account account=new Account("cache-user","org.shiraz.fixture.account");
  if(stage==0&&!manager.addAccountExplicitly(account,"fixture-pw",null))throw new AssertionError("Cache account already exists");
  Bundle options=new Bundle();options.putString("fixture-option","original");
  manager.getAuthToken(account,"cache-token",options,false,new AccountManagerCallback<Bundle>(){public void run(AccountManagerFuture<Bundle> result){
   try{
    String token=result.getResult().getString(AccountManager.KEY_AUTHTOKEN);
    if(token==null||!token.startsWith("synthetic-cache-"))throw new AssertionError("Cache token missing");
    if(stage==1&&!token.equals(previous))throw new AssertionError("Cache hit called provider again");
    if(stage>=2&&token.equals(previous))throw new AssertionError("Invalidated token reused");
    if(!token.equals(manager.peekAuthToken(account,"cache-token")))throw new AssertionError("Fresh token not cached");
    if(stage==1)manager.invalidateAuthToken(account.type,token);
    if(stage==2)manager.clearPassword(account);
    if(stage<3)verifyCache(stage+1,token);
    else{if(!manager.removeAccountExplicitly(account))throw new AssertionError("Cache account cleanup");System.out.println("AUTHENTICATOR_TOKEN_CACHE_OK");verifyDelayedToken(0);}
   }catch(Exception failure){throw new AssertionError(failure);}
  }},null);
 }

 private void verifyDelayedToken(final int stage){
  final AccountManager manager=AccountManager.get(this);final Account account=new Account("delayed-user","org.shiraz.fixture.account");
  if(!manager.addAccountExplicitly(account,"fixture-pw",null))throw new AssertionError("Delayed account exists");
  // Keep the worker alive so cancellation is tested against an actual late send.
  final java.io.Closeable anchor;
  try{anchor=(java.io.Closeable)Class.forName("shiraz.runtime.HostAccounts").getMethod("open",String.class).invoke(null,account.type);}catch(Exception failure){throw new AssertionError(failure);}
  final boolean[] finished={false,false};
  final Runnable finish=new Runnable(){public void run(){
   if(!finished[0]||!finished[1])return;
   if(manager.peekAuthToken(account,"delayed-token")!=null)throw new AssertionError("Delayed response repopulated token cache");
   if(!manager.removeAccountExplicitly(account))throw new AssertionError("Delayed account cleanup");
   try{anchor.close();}catch(Exception failure){throw new AssertionError(failure);}
   if(stage<3)verifyDelayedToken(stage+1);else{System.out.println("AUTHENTICATOR_DELAYED_TOKEN_BARRIERS_OK");verifyCustomToken(0,null);}
  }};
  Bundle options=new Bundle();options.putString("fixture-option","original");
  final AccountManagerFuture<Bundle> pending=manager.getAuthToken(account,"delayed-token",options,false,new AccountManagerCallback<Bundle>(){public void run(AccountManagerFuture<Bundle> result){
   try{
    if(stage==3){try{result.getResult();throw new AssertionError("Canceled delayed token returned");}catch(android.accounts.OperationCanceledException expected){}}
    else if(!"synthetic-fixture-token".equals(result.getResult().getString(AccountManager.KEY_AUTHTOKEN)))throw new AssertionError("Delayed token result");
   }catch(Exception failure){throw new AssertionError(failure);}
   finished[0]=true;finish.run();
  }},null);
  awaitDelayedReady(manager,account,0,new Runnable(){public void run(){
   if(stage==0)manager.invalidateAuthToken(account.type,"synthetic-fixture-token");
   else if(stage==1)manager.clearPassword(account);
   else if(stage==2){if(!manager.removeAccountExplicitly(account)||!manager.addAccountExplicitly(account,"replacement",null))throw new AssertionError("Delayed account replacement");}
   else if(!pending.cancel(true))throw new AssertionError("Delayed cancel lost");
   manager.hasFeatures(account,new String[]{"delayed-release"},new AccountManagerCallback<Boolean>(){public void run(AccountManagerFuture<Boolean> released){
    try{if(!released.getResult())throw new AssertionError("Delayed response not sent");}catch(Exception failure){throw new AssertionError(failure);}
    finished[1]=true;finish.run();
   }},null);
  }});
 }
 private void awaitDelayedReady(final AccountManager manager,final Account account,final int attempt,final Runnable ready){
  manager.hasFeatures(account,new String[]{"delayed-ready"},new AccountManagerCallback<Boolean>(){public void run(AccountManagerFuture<Boolean> result){
   try{if(result.getResult()){ready.run();return;}}catch(Exception failure){throw new AssertionError(failure);}
   if(attempt>=100)throw new AssertionError("Delayed authenticator never received request");
   new Handler(Looper.getMainLooper()).postDelayed(new Runnable(){public void run(){awaitDelayedReady(manager,account,attempt+1,ready);}},10);
  }},null);
 }

 private void verifyCustomToken(final int stage,final String previous){
  final AccountManager manager=AccountManager.get(this);final Account account=new Account("custom-user","org.shiraz.fixture.custom");
  if(stage==0&&!manager.addAccountExplicitly(account,null,null))throw new AssertionError("Custom account exists");
  final String type=stage<3?"custom-live":stage<5?"custom-expired":"custom-noexpiry";
  Bundle options=new Bundle();options.putString("fixture-option","original");
  manager.getAuthToken(account,type,options,false,new AccountManagerCallback<Bundle>(){public void run(AccountManagerFuture<Bundle> future){
   try{
    String token=future.getResult().getString(AccountManager.KEY_AUTHTOKEN);
    if(token==null||!token.startsWith("synthetic-custom-"))throw new AssertionError("Custom token missing");
    if(stage==1&&!token.equals(previous))throw new AssertionError("Custom cache miss");
    if(stage>1&&token.equals(previous))throw new AssertionError("Custom invalidation/expiry ignored");
    if(manager.peekAuthToken(account,type)!=null)throw new AssertionError("Custom token in shared cache");
    if(stage==1)manager.invalidateAuthToken(account.type,token);
    if(stage<6)verifyCustomToken(stage+1,token);
    else{if(!manager.removeAccountExplicitly(account))throw new AssertionError("Custom cleanup");System.out.println("AUTHENTICATOR_CUSTOM_TOKEN_CACHE_OK");verifyAddAccount(0);}
   }catch(Exception failure){throw new AssertionError(failure);}
  }},null);
 }

 private void verifyAddAccount(final int stage){
  final AccountManager manager=AccountManager.get(this);final String type="org.shiraz.fixture.account";
  String[] cases={"create","wrong-type","missing-name","ui"};String[] features={"fixture-feature"};
  Bundle options=new Bundle();options.putString("fixture-option","original");options.putString("androidPackageName","forged");options.putInt("callerUid",0);options.putInt("callerPid",0);
  manager.addAccount(type,cases[stage],features,options,null,new AccountManagerCallback<Bundle>(){public void run(AccountManagerFuture<Bundle> future){
   if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Add account callback thread");
   try{
    if(stage==0){
     Bundle result=future.getResult();Account created=new Account("created-by-provider",type);
     if(result.containsKey(AccountManager.KEY_AUTHTOKEN))throw new AssertionError("Account creation leaked an auth token");
     if(!created.name.equals(result.getString(AccountManager.KEY_ACCOUNT_NAME))||!type.equals(result.getString(AccountManager.KEY_ACCOUNT_TYPE)))throw new AssertionError("Add account result");
     boolean found=false;for(Account account:manager.getAccountsByType(type))if(account.equals(created))found=true;
     if(!found||!manager.removeAccountExplicitly(created))throw new AssertionError("Provider account missing from shared store");
    }else{try{future.getResult();throw new AssertionError("Invalid account creation accepted");}catch(AuthenticatorException expected){}}
   }catch(Exception failure){throw new AssertionError(failure);}
   if(stage<3)verifyAddAccount(stage+1);else{System.out.println("AUTHENTICATOR_PUBLIC_ADD_ACCOUNT_OK");verifyPublicRequest(0);}
  }},null);
  features[0]="changed";options.putString("fixture-option","changed");
 }

}
