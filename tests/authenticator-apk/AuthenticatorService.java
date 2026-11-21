// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.authenticatorfixture;
import android.accounts.*;
import android.os.*;
public class AuthenticatorService extends android.app.Service {
 private AbstractAccountAuthenticator authenticator;
 private volatile AccountAuthenticatorResponse delayedResponse;
 private Bundle delayedResult;
 public void onCreate(){super.onCreate();authenticator=new AbstractAccountAuthenticator(this){
  public Bundle addAccount(AccountAuthenticatorResponse response,String type,String token,String[] features,Bundle options){
   if(Binder.getCallingPid()==android.os.Process.myPid()||checkCallingOrSelfPermission("android.permission.ACCOUNT_MANAGER")!=0||options.getInt("callerUid")!=Binder.getCallingUid()||options.getInt("callerPid")!=Binder.getCallingPid()||!(("cross-add".equals(token)||token!=null&&token.startsWith("ui-")&&!token.startsWith("ui-local-"))?"org.shiraz.activitycaller":getPackageName()).equals(options.getString("androidPackageName"))||!"original".equals(options.getString("fixture-option"))||features==null||features.length!=1||!"fixture-feature".equals(features[0]))throw new AssertionError("Add account caller/options/features");
   if((token!=null&&token.startsWith("ui-"))){
    Bundle ui=new Bundle();android.content.Intent intent=new android.content.Intent();
    intent.setComponent(new android.content.ComponentName(getPackageName(),getPackageName()+".AccountUiActivity"));
    if(token.startsWith("ui-local-")||"ui-private".equals(token))intent.setComponent(new android.content.ComponentName(getPackageName(),getPackageName()+".PrivateAccountUi"));
    if("ui-foreign".equals(token))intent.setComponent(new android.content.ComponentName("org.shiraz.activitycaller","org.shiraz.activitycaller.MainActivity"));
    if("ui-grants".equals(token))intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.putExtra("fixture-mode",token);intent.putExtra("fixture-type",type);ui.putParcelable(AccountManager.KEY_INTENT,intent);return ui;
   }
   Bundle result=new Bundle();result.putString(AccountManager.KEY_ACCOUNT_TYPE,"wrong-type".equals(token)?"wrong":type);
   result.putString(AccountManager.KEY_AUTHTOKEN,"synthetic-add-account-token-must-not-escape");
   if(!"missing-name".equals(token))result.putString(AccountManager.KEY_ACCOUNT_NAME,"created-by-provider");
   if("ui".equals(token))result.putParcelable(AccountManager.KEY_INTENT,new android.content.Intent("test.LOGIN"));
   if(("create".equals(token)||"cross-add".equals(token))&&!AccountManager.get(AuthenticatorService.this).addAccountExplicitly(new Account("created-by-provider",type),null,null))throw new AssertionError("Provider account creation failed");
   return result;
  }
  public Bundle confirmCredentials(AccountAuthenticatorResponse response,Account account,Bundle options){throw new UnsupportedOperationException();}
  public Bundle getAuthToken(AccountAuthenticatorResponse response,Account account,String type,Bundle options){
   if(Binder.getCallingPid()==android.os.Process.myPid()||checkCallingOrSelfPermission("android.permission.ACCOUNT_MANAGER")!=0||options.getInt("callerUid")!=Binder.getCallingUid()||options.getInt("callerPid")!=Binder.getCallingPid()||!(type.startsWith("ui-token-")?"org.shiraz.activitycaller":getPackageName()).equals(options.getString("androidPackageName"))||!"original".equals(options.getString("fixture-option")))throw new AssertionError("Token caller options");
   if(type.startsWith("ui-token-")){
    Bundle ui=new Bundle();android.content.Intent intent=new android.content.Intent();
    intent.setComponent(new android.content.ComponentName(getPackageName(),getPackageName()+".AccountUiActivity"));
    intent.putExtra("fixture-mode",type);intent.putExtra("fixture-type",account.type);
    intent.putExtra("fixture-account",account.name);ui.putParcelable(AccountManager.KEY_INTENT,intent);return ui;
   }
   Bundle result=new Bundle();result.putString(AccountManager.KEY_ACCOUNT_NAME,"wrong-account".equals(type)?"wrong":account.name);result.putString(AccountManager.KEY_ACCOUNT_TYPE,account.type);
   if(!"missing".equals(type))result.putString(AccountManager.KEY_AUTHTOKEN,"synthetic-fixture-token");
   if("cache-user".equals(account.name)){
    android.content.SharedPreferences counts=getSharedPreferences("token-count",0);int count=counts.getInt("count",0)+1;
    if(!counts.edit().putInt("count",count).commit())throw new AssertionError("Token counter persistence");
    result.putString(AccountManager.KEY_AUTHTOKEN,"synthetic-cache-"+count);
   }
   if(account.type.endsWith(".custom")){
    android.content.SharedPreferences counts=getSharedPreferences("custom-token-count",0);int count=counts.getInt("count",0)+1;
    if(!counts.edit().putInt("count",count).commit())throw new AssertionError("Custom counter persistence");
    result.putString(AccountManager.KEY_AUTHTOKEN,"synthetic-custom-"+count);
    if(!"custom-noexpiry".equals(type))result.putLong(AbstractAccountAuthenticator.KEY_CUSTOM_TOKEN_EXPIRY,"custom-expired".equals(type)?1:System.currentTimeMillis()+60000);
   }
   if("wrong-type".equals(type))result.putInt(AccountManager.KEY_AUTHTOKEN,123);
   if("ui".equals(type))result.putParcelable(AccountManager.KEY_INTENT,new android.content.Intent("test.AUTH"));
   if("expiry".equals(type))result.putString(AbstractAccountAuthenticator.KEY_CUSTOM_TOKEN_EXPIRY,"invalid");
   if("delayed-token".equals(type)){
    if(delayedResponse!=null)throw new AssertionError("Overlapping delayed token");
    delayedResult=result;delayedResponse=response;return null;
   }
   return result;
  }
  public String getAuthTokenLabel(String type){
   if(!"fixture-token".equals(type)||Binder.getCallingPid()==android.os.Process.myPid()||Binder.getCallingUid()!=android.os.Process.myUid()||checkCallingOrSelfPermission("android.permission.ACCOUNT_MANAGER")!=0)throw new AssertionError("Authenticator caller/scope");
   return "fixture-label";
  }
  public Bundle updateCredentials(AccountAuthenticatorResponse response,Account account,String type,Bundle options){throw new UnsupportedOperationException();}
  public Bundle editProperties(AccountAuthenticatorResponse response,String type){throw new UnsupportedOperationException();}
  public Bundle hasFeatures(AccountAuthenticatorResponse response,Account account,String[] features)throws NetworkErrorException{
   if(Binder.getCallingPid()==android.os.Process.myPid()||checkCallingOrSelfPermission("android.permission.ACCOUNT_MANAGER")!=0||features.length!=1)throw new AssertionError("Async authenticator caller");
   if("listener-add".equals(features[0])||"listener-remove".equals(features[0])){
    if(!"1".equals(System.getenv("SHIRAZ_TEST_ACCOUNT_LISTENERS"))||!"signed-listener-user".equals(account.name)||!"org.shiraz.fixture.account".equals(account.type))throw new AssertionError("Listener fixture identity");
    AccountManager manager=AccountManager.get(AuthenticatorService.this);
    boolean changed="listener-add".equals(features[0])?manager.addAccountExplicitly(account,null,null):manager.removeAccountExplicitly(account);
    Bundle result=new Bundle();result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT,changed);return result;
   }
   if("delayed-ready".equals(features[0])){Bundle result=new Bundle();result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT,delayedResponse!=null);return result;}
   if("delayed-release".equals(features[0])){
    AccountAuthenticatorResponse pending=delayedResponse;if(pending==null)throw new AssertionError("No delayed token");
    pending.onResult(delayedResult);delayedResponse=null;delayedResult=null;
    Bundle result=new Bundle();result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT,true);return result;
   }
   if("network".equals(features[0]))throw new NetworkErrorException("fixture-network-detail");
   if("die".equals(features[0]))Runtime.getRuntime().halt(23);
   if("pending".equals(features[0]))return null;
   Bundle result=new Bundle();if("malformed".equals(features[0]))result.putString(AccountManager.KEY_BOOLEAN_RESULT,"true");else result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT,"fixture-feature".equals(features[0]));return result;
  }
 };}
 public IBinder onBind(android.content.Intent intent){
  if(!AccountManager.ACTION_AUTHENTICATOR_INTENT.equals(intent.getAction())||checkSelfPermission("android.permission.ACCOUNT_MANAGER")==0)throw new AssertionError("Authenticator binding or ambient grant");
  return authenticator.getIBinder();
 }
}
