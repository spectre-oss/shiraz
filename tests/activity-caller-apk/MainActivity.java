// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.activitycaller;
public final class MainActivity extends android.app.Activity {
 protected void onCreate(android.os.Bundle state){
  super.onCreate(state);
  android.widget.TextView text=new android.widget.TextView(this);text.setText("Waiting for explicit activity acceptance");setContentView(text);
  System.out.println("EXPLICIT_ACTIVITY_CALLER_READY");
  if("1".equals(System.getenv("SHIRAZ_TEST_CROSS_ADD"))){crossAdd();return;}
  if("1".equals(System.getenv("SHIRAZ_TEST_ACCOUNT_LISTENERS"))){org.shiraz.fixture.AccountListenerAcceptance.run(this);return;}
  if(getIntent().getBooleanExtra("restarted",false)){
   if(getIntent().getIntExtra("previous-pid",-1)==android.os.Process.myPid())throw new AssertionError("Restart retained old process");
   if(!isTaskRoot())throw new AssertionError("Restart not task root");
   System.out.println("GUEST_RESTART_MAIN_OK");finish();return;
  }
  if(System.getenv("SHIRAZ_TEST_GUEST_ACTIVITY")!=null){
   android.content.Intent launch=new android.content.Intent(this,SecondaryActivity.class).putExtra("guest-handoff","original").putExtra("parent-pid",android.os.Process.myPid());
   try{startActivityForResult(launch,7);throw new AssertionError("Cross-process result accepted");}catch(UnsupportedOperationException expected){}
   startActivity(launch);
   launch.putExtra("guest-handoff","mutated");
   System.out.println("GUEST_ACTIVITY_HANDOFF_OK");if(!"kill".equals(System.getenv("SHIRAZ_TEST_GUEST_ACTIVITY")))finish();return;
  }
  final String mode=System.getenv("SHIRAZ_TEST_ACCOUNT_UI");
  if(mode!=null&&mode.startsWith("token-")){requestToken(mode);return;}
  if(mode!=null){
   android.os.Bundle options=new android.os.Bundle();options.putString("fixture-option","original");
   android.accounts.AccountManager.get(this).addAccount("org.shiraz.fixture.account","ui-"+mode,new String[]{"fixture-feature"},options,this,new android.accounts.AccountManagerCallback<android.os.Bundle>(){
    public void run(android.accounts.AccountManagerFuture<android.os.Bundle> future){
     if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new AssertionError("Callback thread");
     try{
      android.os.Bundle result=future.getResult();
      if(!"result".equals(mode)||!"ui-fixture".equals(result.getString(android.accounts.AccountManager.KEY_ACCOUNT_NAME))||!"org.shiraz.fixture.account".equals(result.getString(android.accounts.AccountManager.KEY_ACCOUNT_TYPE)))throw new AssertionError("Account UI result");
     }catch(android.accounts.OperationCanceledException expected){if(!"cancel".equals(mode))throw new AssertionError(expected);}
     catch(android.accounts.AuthenticatorException expected){if(!"foreign".equals(mode)&&!"grants".equals(mode))throw new AssertionError(expected);}
     catch(SecurityException expected){if(!"private".equals(mode))throw new AssertionError(expected);}
     catch(Exception failure){throw new AssertionError(failure);}
     System.out.println("ACCOUNT_UI_CALLER_OK mode="+mode);finish();
    }
   },null);
  }
 }
 private void crossAdd(){
  final android.accounts.AccountManager manager=android.accounts.AccountManager.get(this);
  final android.accounts.Account account=new android.accounts.Account("created-by-provider","org.shiraz.fixture.account");
  android.os.Bundle options=new android.os.Bundle();options.putString("fixture-option","original");options.putInt("callerUid",0);options.putString("androidPackageName","forged");
  manager.addAccount(account.type,"cross-add",new String[]{"fixture-feature"},options,null,new android.accounts.AccountManagerCallback<android.os.Bundle>(){
   public void run(android.accounts.AccountManagerFuture<android.os.Bundle> future){
    try{
     android.os.Bundle result=future.getResult();
     if(!account.name.equals(result.getString(android.accounts.AccountManager.KEY_ACCOUNT_NAME))||!account.type.equals(result.getString(android.accounts.AccountManager.KEY_ACCOUNT_TYPE))||result.containsKey(android.accounts.AccountManager.KEY_AUTHTOKEN))throw new AssertionError("Cross-signer add result");
     if(manager.getAccountsByType(account.type).length!=0)throw new AssertionError("Add granted account visibility");
     try{manager.getPassword(account);throw new AssertionError("Add granted password access");}catch(SecurityException expected){}
     try{manager.peekAuthToken(account,"fixture");throw new AssertionError("Add granted token access");}catch(SecurityException expected){}
     try{manager.addAccountExplicitly(new android.accounts.Account("forged",account.type),null,null);throw new AssertionError("Add granted account ownership");}catch(SecurityException expected){}
     if(checkSelfPermission("android.permission.ACCOUNT_MANAGER")==0)throw new AssertionError("Ambient account manager permission");
     System.out.println("CROSS_SIGNER_ADD_ACCOUNT_OK");finish();
    }catch(Exception error){throw new AssertionError(error);}
   }
  },null);
 }
 private void requestToken(final String mode){
  final android.accounts.AccountManager manager=android.accounts.AccountManager.get(this);
  final android.accounts.Account account=new android.accounts.Account("ui-token-user","token-custom".equals(mode)?"org.shiraz.fixture.custom":"org.shiraz.fixture.account");
  try{manager.getAuthToken(account,"ui-"+mode,null,(android.app.Activity)null,null,null);throw new AssertionError("Null token Activity accepted");}
  catch(IllegalArgumentException expected){}
  if(!manager.addAccountExplicitly(account,null,null))throw new AssertionError("Token fixture account creation");
  android.os.Bundle options=new android.os.Bundle();options.putString("fixture-option","original");
  final long started=android.os.SystemClock.elapsedRealtime();
  manager.getAuthToken(account,"ui-"+mode,options,this,new android.accounts.AccountManagerCallback<android.os.Bundle>(){
   public void run(android.accounts.AccountManagerFuture<android.os.Bundle> future){
    if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new AssertionError("Token callback thread");
    try{
     android.os.Bundle result=future.getResult();
     if((!"token-result".equals(mode)&&!"token-custom".equals(mode))||!"synthetic-ui-token".equals(result.getString(android.accounts.AccountManager.KEY_AUTHTOKEN)))throw new AssertionError("UI token result");
     if("token-custom".equals(mode)){if(manager.peekAuthToken(account,"ui-"+mode)!=null)throw new AssertionError("Custom UI token leaked to standard cache");}
     else if(!"synthetic-ui-token".equals(manager.peekAuthToken(account,"ui-"+mode)))throw new AssertionError("UI token cache");
     // Missing provider-required options makes an unexpected cache miss fail.
     manager.getAuthToken(account,"ui-"+mode,null,false,new android.accounts.AccountManagerCallback<android.os.Bundle>(){
      public void run(android.accounts.AccountManagerFuture<android.os.Bundle> cached){
       try{if(!"synthetic-ui-token".equals(cached.getResult().getString(android.accounts.AccountManager.KEY_AUTHTOKEN)))throw new AssertionError("Cached UI token");}
       catch(Exception failure){throw new AssertionError(failure);}
       completeToken(manager,account,mode);
      }
     },null);return;
    }catch(android.accounts.OperationCanceledException expected){if(!"token-cancel".equals(mode))throw new AssertionError(expected);}
    catch(android.accounts.AuthenticatorException expected){if(!"token-wrong-account".equals(mode)&&!"token-missing".equals(mode)&&!"token-expiry".equals(mode)&&!"token-die".equals(mode))throw new AssertionError(expected);}
    catch(Exception failure){throw new AssertionError(failure);}
    if(manager.peekAuthToken(account,"ui-"+mode)!=null)throw new AssertionError("Invalid UI result cached");
    if("token-die".equals(mode)){
     if(android.os.SystemClock.elapsedRealtime()-started>=15000)throw new AssertionError("UI death was not prompt");
     manager.hasFeatures(account,new String[]{"fixture-feature"},new android.accounts.AccountManagerCallback<Boolean>(){
      public void run(android.accounts.AccountManagerFuture<Boolean> recovered){
       try{if(!recovered.getResult())throw new AssertionError("Authenticator recovery");}catch(Exception failure){throw new AssertionError(failure);}
       System.out.println("ACCOUNT_UI_DEATH_RECOVERY_OK");completeToken(manager,account,mode);
      }
     },null);return;
    }
    completeToken(manager,account,mode);
   }
  },null);
 }
 private void completeToken(android.accounts.AccountManager manager,android.accounts.Account account,String mode){
  if(!manager.removeAccountExplicitly(account))throw new AssertionError("Token fixture account cleanup");
  System.out.println("ACCOUNT_UI_CALLER_OK mode="+mode);finish();
 }

}
