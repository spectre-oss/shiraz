// SPDX-License-Identifier: GPL-3.0-only
package android.accounts;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.*;

/** Broker-backed managed accounts; standalone callers retain app-local storage. */
public final class AccountManager {
    public static final String KEY_ACCOUNT_NAME="authAccount",KEY_ACCOUNT_TYPE="accountType",KEY_AUTHTOKEN="authtoken";
    public static final String ACTION_AUTHENTICATOR_INTENT="android.accounts.AccountAuthenticator";
    public static final String KEY_BOOLEAN_RESULT="booleanResult",KEY_AUTH_TOKEN_LABEL="authTokenLabelKey",KEY_INTENT="intent",KEY_ACCOUNT_MANAGER_RESPONSE="accountManagerResponse",KEY_ERROR_CODE="errorCode",KEY_ERROR_MESSAGE="errorMessage",KEY_ACCOUNT_SESSION_BUNDLE="accountSessionBundle",KEY_PASSWORD="password",KEY_ACCOUNT_STATUS_TOKEN="accountStatusToken";
    public static final int ERROR_CODE_REMOTE_EXCEPTION=1,ERROR_CODE_NETWORK_ERROR=3,ERROR_CODE_CANCELED=4,ERROR_CODE_INVALID_RESPONSE=5,ERROR_CODE_UNSUPPORTED_OPERATION=6,ERROR_CODE_BAD_ARGUMENTS=7,ERROR_CODE_BAD_REQUEST=8,ERROR_CODE_BAD_AUTHENTICATION=9;
    private static final Map<Context,AccountManager> managers=new WeakHashMap<Context,AccountManager>();
    private final SharedPreferences store;
    private LocalAccountListeners localListeners;
    private final String packageName;
    private AccountManager(Context context){packageName=context.getPackageName();store=context.getSharedPreferences("shiraz.account-manager",Context.MODE_PRIVATE);}
    public static AccountManager get(Context context){
        if(context==null)throw new IllegalArgumentException("context");
        Context app=context.getApplicationContext();if(app==null)app=context;
        synchronized(managers){AccountManager manager=managers.get(app);if(manager==null){manager=new AccountManager(app);managers.put(app,manager);}return manager;}
    }
    private static String hex(String text){StringBuilder out=new StringBuilder();for(int i=0;i<text.length();i++){int c=text.charAt(i);for(int shift=12;shift>=0;shift-=4)out.append(Character.forDigit((c>>>shift)&15,16));}return out.toString();}
    private static String id(Account account){if(account==null)throw new IllegalArgumentException("account");return hex(account.type)+"/"+hex(account.name);}
    private static String prefix(Account account){return "a:"+id(account)+":";}
    private Set<String> ids(){return new HashSet<String>(store.getStringSet("accounts",Collections.<String>emptySet()));}
    private static void commit(SharedPreferences.Editor edit){if(!edit.commit())throw new IllegalStateException("Cannot persist account data");}
    public static final String KEY_CALLER_UID="callerUid",KEY_CALLER_PID="callerPid",KEY_ANDROID_PACKAGE_NAME="androidPackageName";
    public AccountManagerFuture<Bundle> getAuthToken(Account account,String authTokenType,boolean notifyAuthFailure,AccountManagerCallback<Bundle> callback,android.os.Handler handler){
        return getAuthToken(account,authTokenType,null,notifyAuthFailure,callback,handler);
    }
    public AccountManagerFuture<Bundle> getAuthToken(final Account account,final String authTokenType,Bundle options,boolean notifyAuthFailure,AccountManagerCallback<Bundle> callback,android.os.Handler handler){
        if(notifyAuthFailure)throw new UnsupportedOperationException("Account authentication notifications are not implemented");
        return requestAuthToken(account,authTokenType,options,null,callback,handler);
    }
    public AccountManagerFuture<Bundle> getAuthToken(Account account,String authTokenType,Bundle options,android.app.Activity activity,AccountManagerCallback<Bundle> callback,android.os.Handler handler){
        if(activity==null)throw new IllegalArgumentException("Activity required");
        return requestAuthToken(account,authTokenType,options,activity,callback,handler);
    }
    private AccountManagerFuture<Bundle> requestAuthToken(final Account account,final String authTokenType,Bundle options,android.app.Activity activity,AccountManagerCallback<Bundle> callback,android.os.Handler handler){
        if(account==null||authTokenType==null||authTokenType.isEmpty()||authTokenType.length()>1024||authTokenType.indexOf(0)>=0)throw new IllegalArgumentException("Account and token type required");
        final Bundle supplied=options==null?new Bundle():new Bundle(options);
        supplied.putString(KEY_ANDROID_PACKAGE_NAME,packageName);supplied.putInt(KEY_CALLER_UID,android.os.Process.myUid());supplied.putInt(KEY_CALLER_PID,android.os.Process.myPid());
        return shiraz.runtime.HostAccountSessions.start(account.type,new shiraz.runtime.HostAccountSessions.Request<Bundle>(){
            private shiraz.runtime.ServiceBrokerClient.TokenPreparation prepared;
            public Bundle prepare()throws java.io.IOException{
                prepared=shiraz.runtime.HostAccounts.prepareToken(account,authTokenType);
                if(prepared.customTokens)prepared=shiraz.runtime.HostAccounts.prepareCustomToken(account,authTokenType);
                if(prepared.token==null||prepared.token.isEmpty()||prepared.customTokens&&(prepared.expiresMs==null||prepared.expiresMs<=System.currentTimeMillis()))return null;
                Bundle cached=new Bundle();cached.putString(KEY_ACCOUNT_NAME,account.name);cached.putString(KEY_ACCOUNT_TYPE,account.type);cached.putString(KEY_AUTHTOKEN,prepared.token);if(prepared.customTokens)cached.putLong(AbstractAccountAuthenticator.KEY_CUSTOM_TOKEN_EXPIRY,prepared.expiresMs);return cached;
            }
            public void connected(shiraz.runtime.ServiceBrokerClient.Resolution resolution)throws AuthenticatorException{
                if(prepared.ownerUid!=resolution.uid||!prepared.ownerSha256.equals(resolution.apkSha256))throw new AuthenticatorException("Authenticator changed during token request");
            }
            public void send(IAccountAuthenticator service,IAccountAuthenticatorResponse response)throws android.os.RemoteException{service.getAuthToken(response,account,authTokenType,supplied);}
            public Bundle decode(Bundle result)throws java.io.IOException,AuthenticatorException{
                Object name=result.get(KEY_ACCOUNT_NAME),type=result.get(KEY_ACCOUNT_TYPE),token=result.get(KEY_AUTHTOKEN);
                if(!account.name.equals(name)||!account.type.equals(type)||!(token instanceof String)||((String)token).isEmpty()||((String)token).length()>16384||((String)token).indexOf(0)>=0)throw new AuthenticatorException("Invalid authenticator token response");
                if(result.containsKey(AbstractAccountAuthenticator.KEY_CUSTOM_TOKEN_EXPIRY)){
                    Object expiry=result.get(AbstractAccountAuthenticator.KEY_CUSTOM_TOKEN_EXPIRY);
                    if(!(expiry instanceof Long)||((Long)expiry)<0)throw new AuthenticatorException("Invalid authenticator token expiry");
                }
                // A rejected revision leaves this fresh response uncached. Missing
                // accounts may still receive tokens from their authenticator.
                if(!prepared.customTokens)shiraz.runtime.HostAccounts.commitToken(account,authTokenType,(String)token,prepared);
                else {
                    Object expiry=result.get(AbstractAccountAuthenticator.KEY_CUSTOM_TOKEN_EXPIRY);
                    if(expiry instanceof Long&&((Long)expiry)>System.currentTimeMillis())shiraz.runtime.HostAccounts.commitCustomToken(account,authTokenType,(String)token,prepared,(Long)expiry);
                }
                return new Bundle(result);
            }
        },callback,handler,activity);
    }
    public AccountManagerFuture<Bundle> addAccount(final String accountType,final String authTokenType,String[] requiredFeatures,Bundle options,android.app.Activity activity,AccountManagerCallback<Bundle> callback,android.os.Handler handler){
        if(accountType==null||accountType.isEmpty()||accountType.length()>1024||accountType.indexOf(0)>=0)throw new IllegalArgumentException("Account type required");
        if(authTokenType!=null&&(authTokenType.length()>1024||authTokenType.indexOf(0)>=0))throw new IllegalArgumentException("Invalid token type");
        final String[] features=requiredFeatures==null?null:requiredFeatures.clone();
        if(features!=null){if(features.length>128)throw new IllegalArgumentException("Account feature count limit");for(String feature:features)if(feature==null||feature.length()>1024||feature.indexOf(0)>=0)throw new IllegalArgumentException("Invalid account feature");}
        final Bundle supplied=options==null?new Bundle():new Bundle(options);
        supplied.putString(KEY_ANDROID_PACKAGE_NAME,packageName);supplied.putInt(KEY_CALLER_UID,android.os.Process.myUid());supplied.putInt(KEY_CALLER_PID,android.os.Process.myPid());
        return shiraz.runtime.HostAccountSessions.start(accountType,new shiraz.runtime.HostAccountSessions.Request<Bundle>(){
            public boolean addAccountOnly(){return true;}
            public void send(IAccountAuthenticator service,IAccountAuthenticatorResponse response)throws android.os.RemoteException{service.addAccount(response,accountType,authTokenType,features,supplied);}
            public Bundle decode(Bundle result)throws AuthenticatorException{
                Object name=result.get(KEY_ACCOUNT_NAME),type=result.get(KEY_ACCOUNT_TYPE);
                if(!(name instanceof String)||((String)name).isEmpty()||((String)name).length()>1024||((String)name).indexOf(0)>=0||!accountType.equals(type))throw new AuthenticatorException("Invalid authenticator account result");
                // The authenticator owns explicit account creation and its side effects.
                // Account creation does not authorize token disclosure. Match the
                // system addAccount session even when the provider includes one.
                Bundle accountResult=new Bundle(result);
                accountResult.remove(KEY_AUTHTOKEN);
                return accountResult;
            }
        },callback,handler,activity);
    }
    public String blockingGetAuthToken(Account account,String authTokenType,boolean notifyAuthFailure)throws OperationCanceledException,java.io.IOException,AuthenticatorException{
        if(android.os.Looper.getMainLooper()!=null&&android.os.Looper.myLooper()==android.os.Looper.getMainLooper())throw new IllegalStateException("Account token request cannot block the main thread");
        return getAuthToken(account,authTokenType,notifyAuthFailure,null,null).getResult().getString(KEY_AUTHTOKEN);
    }
    public AccountManagerFuture<Boolean> hasFeatures(final Account account,String[] features,AccountManagerCallback<Boolean> callback,android.os.Handler handler){
        if(account==null||features==null)throw new IllegalArgumentException("Account and features required");
        if(features.length>128)throw new IllegalArgumentException("Account feature count limit");
        final String[] copy=features.clone();for(String feature:copy)if(feature==null||feature.length()>1024||feature.indexOf(0)>=0)throw new IllegalArgumentException("Invalid account feature");
        return shiraz.runtime.HostAccountSessions.start(account.type,new shiraz.runtime.HostAccountSessions.Request<Boolean>(){
            public void send(IAccountAuthenticator service,IAccountAuthenticatorResponse response)throws android.os.RemoteException{service.hasFeatures(response,account,copy);}
            public Boolean decode(Bundle result)throws AuthenticatorException{Object value=result.get(KEY_BOOLEAN_RESULT);if(!(value instanceof Boolean))throw new AuthenticatorException("Authenticator boolean result required");return (Boolean)value;}
        },callback,handler);
    }
    public void addOnAccountsUpdatedListener(OnAccountsUpdateListener listener,android.os.Handler handler,boolean updateImmediately){
        addOnAccountsUpdatedListener(listener,handler,updateImmediately,null);
    }
    public void addOnAccountsUpdatedListener(OnAccountsUpdateListener listener,android.os.Handler handler,boolean updateImmediately,String[] accountTypes){
        if(shiraz.runtime.HostAccounts.registered())shiraz.runtime.HostAccountListeners.add(this,listener,handler,updateImmediately,accountTypes);
        else {
            LocalAccountListeners local;synchronized(this){if(localListeners==null)localListeners=new LocalAccountListeners(this,store);local=localListeners;}
            local.add(listener,handler,updateImmediately,accountTypes);
        }
    }
    public void removeOnAccountsUpdatedListener(OnAccountsUpdateListener listener){
        shiraz.runtime.HostAccountListeners.remove(this,listener);
        LocalAccountListeners local;synchronized(this){local=localListeners;}
        if(local!=null)local.remove(listener);
    }
    public AuthenticatorDescription[] getAuthenticatorTypes(){return shiraz.runtime.HostAccounts.authenticators();}
    public synchronized Account[] getAccounts(){return getAccountsByType(null);}
    public synchronized Account[] getAccountsByType(String type){
        if(shiraz.runtime.HostAccounts.registered())return (Account[])shiraz.runtime.HostAccounts.operation("list",null,type,null,null);
        ArrayList<Account> result=new ArrayList<Account>();
        for(String id:ids()){String prefix="a:"+id+":";String name=store.getString(prefix+"name",null),kind=store.getString(prefix+"type",null);if(name!=null&&kind!=null&&(type==null||type.equals(kind)))result.add(new Account(name,kind));}
        return result.toArray(new Account[result.size()]);
    }
    public synchronized boolean addAccountExplicitly(Account account,String password,Bundle extras){
        if(shiraz.runtime.HostAccounts.registered())return (Boolean)shiraz.runtime.HostAccounts.operation("add",account,null,password,extras);
        String id=id(account),prefix=prefix(account);Set<String> all=ids();
        if(all.contains(id))return false;
        if(all.size()>=1024)throw new IllegalStateException("Account store limit");
        all.add(id);SharedPreferences.Editor edit=store.edit().putStringSet("accounts",all).putString(prefix+"name",account.name).putString(prefix+"type",account.type).putString(prefix+"password",password);
        if(extras!=null)for(String key:extras.keySet())edit.putString(prefix+"data:"+key,extras.getString(key));
        return edit.commit();
    }
    public synchronized boolean removeAccountExplicitly(Account account){
        if(shiraz.runtime.HostAccounts.registered())return (Boolean)shiraz.runtime.HostAccounts.operation("remove",account,null,null,null);
        Set<String> all=ids();if(!all.remove(id(account)))return false;
        String prefix=prefix(account);SharedPreferences.Editor edit=store.edit().putStringSet("accounts",all);
        for(String key:store.getAll().keySet())if(key.startsWith(prefix))edit.remove(key);
        return edit.commit();
    }
    private String key(Account account,String category,String key){if(key==null)throw new IllegalArgumentException("key");return prefix(account)+category+key;}
    public synchronized String getUserData(Account account,String key){if(shiraz.runtime.HostAccounts.registered()){return (String)shiraz.runtime.HostAccounts.operation("get_data",account,key,null,null);}return store.getString(key(account,"data:",key),null);}
    public synchronized void setUserData(Account account,String key,String value){if(shiraz.runtime.HostAccounts.registered()){shiraz.runtime.HostAccounts.operation("set_data",account,key,value,null);return;}String path=key(account,"data:",key);if(ids().contains(id(account)))commit(store.edit().putString(path,value));}
    public synchronized String getPassword(Account account){if(shiraz.runtime.HostAccounts.registered()){return (String)shiraz.runtime.HostAccounts.operation("get_password",account,null,null,null);}return store.getString(prefix(account)+"password",null);}
    public synchronized void setPassword(Account account,String password){if(shiraz.runtime.HostAccounts.registered()){shiraz.runtime.HostAccounts.operation("set_password",account,null,password,null);return;}if(ids().contains(id(account)))commit(store.edit().putString(prefix(account)+"password",password));}
    public void clearPassword(Account account){setPassword(account,null);}
    public synchronized String peekAuthToken(Account account,String tokenType){if(shiraz.runtime.HostAccounts.registered()){return (String)shiraz.runtime.HostAccounts.operation("get_token",account,tokenType,null,null);}return store.getString(key(account,"token:",tokenType),null);}
    public synchronized void setAuthToken(Account account,String tokenType,String token){if(shiraz.runtime.HostAccounts.registered()){shiraz.runtime.HostAccounts.operation("set_token",account,tokenType,token,null);return;}String path=key(account,"token:",tokenType);if(ids().contains(id(account)))commit(store.edit().putString(path,token));}
    public synchronized void invalidateAuthToken(String accountType,String token){
        if(accountType==null)throw new IllegalArgumentException("accountType");if(token==null)return;
        if(shiraz.runtime.HostAccounts.registered()){shiraz.runtime.HostAccounts.operation("invalidate",new Account("_",accountType),null,token,null);return;}
        Map<String,?> values=store.getAll();SharedPreferences.Editor edit=store.edit();
        for(Account account:getAccountsByType(accountType)){String prefix=prefix(account)+"token:";for(Map.Entry<String,?> entry:values.entrySet())if(entry.getKey().startsWith(prefix)&&token.equals(entry.getValue()))edit.remove(entry.getKey());}
        commit(edit);
    }
}
