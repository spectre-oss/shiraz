// SPDX-License-Identifier: GPL-3.0-only
package android.accounts;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.*;

/** Persistent accounts owned by this application; no host-wide account provider. */
public final class AccountManager {
    public static final String KEY_ACCOUNT_NAME="authAccount",KEY_ACCOUNT_TYPE="accountType",KEY_AUTHTOKEN="authtoken";
    private static final Map<Context,AccountManager> managers=new WeakHashMap<Context,AccountManager>();
    private final SharedPreferences store;
    private AccountManager(Context context){store=context.getSharedPreferences("shiraz.account-manager",Context.MODE_PRIVATE);}
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
    public synchronized Account[] getAccounts(){return getAccountsByType(null);}
    public synchronized Account[] getAccountsByType(String type){
        ArrayList<Account> result=new ArrayList<Account>();
        for(String id:ids()){String prefix="a:"+id+":";String name=store.getString(prefix+"name",null),kind=store.getString(prefix+"type",null);if(name!=null&&kind!=null&&(type==null||type.equals(kind)))result.add(new Account(name,kind));}
        return result.toArray(new Account[result.size()]);
    }
    public synchronized boolean addAccountExplicitly(Account account,String password,Bundle extras){
        String id=id(account),prefix=prefix(account);Set<String> all=ids();
        if(all.contains(id))return false;
        if(all.size()>=1024)throw new IllegalStateException("Account store limit");
        all.add(id);SharedPreferences.Editor edit=store.edit().putStringSet("accounts",all).putString(prefix+"name",account.name).putString(prefix+"type",account.type).putString(prefix+"password",password);
        if(extras!=null)for(String key:extras.keySet())edit.putString(prefix+"data:"+key,extras.getString(key));
        return edit.commit();
    }
    public synchronized boolean removeAccountExplicitly(Account account){
        Set<String> all=ids();if(!all.remove(id(account)))return false;
        String prefix=prefix(account);SharedPreferences.Editor edit=store.edit().putStringSet("accounts",all);
        for(String key:store.getAll().keySet())if(key.startsWith(prefix))edit.remove(key);
        return edit.commit();
    }
    private String key(Account account,String category,String key){if(key==null)throw new IllegalArgumentException("key");return prefix(account)+category+key;}
    public synchronized String getUserData(Account account,String key){return store.getString(key(account,"data:",key),null);}
    public synchronized void setUserData(Account account,String key,String value){String path=key(account,"data:",key);if(ids().contains(id(account)))commit(store.edit().putString(path,value));}
    public synchronized String getPassword(Account account){return store.getString(prefix(account)+"password",null);}
    public synchronized void setPassword(Account account,String password){if(ids().contains(id(account)))commit(store.edit().putString(prefix(account)+"password",password));}
    public void clearPassword(Account account){setPassword(account,null);}
    public synchronized String peekAuthToken(Account account,String tokenType){return store.getString(key(account,"token:",tokenType),null);}
    public synchronized void setAuthToken(Account account,String tokenType,String token){String path=key(account,"token:",tokenType);if(ids().contains(id(account)))commit(store.edit().putString(path,token));}
    public synchronized void invalidateAuthToken(String accountType,String token){
        if(accountType==null)throw new IllegalArgumentException("accountType");if(token==null)return;
        Map<String,?> values=store.getAll();SharedPreferences.Editor edit=store.edit();
        for(Account account:getAccountsByType(accountType)){String prefix=prefix(account)+"token:";for(Map.Entry<String,?> entry:values.entrySet())if(entry.getKey().startsWith(prefix)&&token.equals(entry.getValue()))edit.remove(entry.getKey());}
        commit(edit);
    }
}
