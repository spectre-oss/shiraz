// SPDX-License-Identifier: GPL-3.0-only
package android.accounts;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/** App-local notifications for launches without a registered host session. */
final class LocalAccountListeners implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final AccountManager manager;
    private final SharedPreferences store;
    private final IdentityHashMap<OnAccountsUpdateListener,Registration> listeners=new IdentityHashMap<OnAccountsUpdateListener,Registration>();
    LocalAccountListeners(AccountManager manager,SharedPreferences store){this.manager=manager;this.store=store;}
    private final class Registration {
        final OnAccountsUpdateListener listener;final Handler handler;final Set<String> types;
        Account[] previous,pending;boolean active=true,queued;
        final Runnable delivery=new Runnable(){public void run(){
            Account[] accounts;
            synchronized(manager){queued=false;accounts=pending;pending=null;if(!active)return;}
            listener.onAccountsUpdated(accounts.clone());
        }};
        Registration(OnAccountsUpdateListener listener,Handler handler,String[] types){
            this.listener=listener;this.handler=handler;
            this.types=types==null?null:new HashSet<String>(Arrays.asList(types.clone()));
        }
        Account[] select(Account[] accounts){
            ArrayList<Account> selected=new ArrayList<Account>();
            for(Account account:accounts)if(types==null||types.contains(account.type))selected.add(account);
            java.util.Collections.sort(selected,new java.util.Comparator<Account>(){public int compare(Account a,Account b){int type=a.type.compareTo(b.type);return type!=0?type:a.name.compareTo(b.name);}});
            return selected.toArray(new Account[selected.size()]);
        }
        void publish(Account[] accounts){
            pending=accounts;
            if(!queued){queued=true;if(!handler.post(delivery)){queued=false;pending=null;}}
        }
    }
    void add(OnAccountsUpdateListener listener,Handler handler,boolean immediately,String[] types){
        if(listener==null)throw new IllegalArgumentException("listener");
        Handler target=handler==null?new Handler(Looper.getMainLooper()):handler;
        synchronized(manager){
            if(listeners.containsKey(listener))throw new IllegalStateException("Account listener already registered");
            if(listeners.size()>=1024)throw new IllegalStateException("Account listener limit");
            Registration item=new Registration(listener,target,types);
            boolean first=listeners.isEmpty();
            if(first)store.registerOnSharedPreferenceChangeListener(this);
            try{item.previous=item.select(manager.getAccounts());listeners.put(listener,item);}
            catch(RuntimeException|Error error){if(first)store.unregisterOnSharedPreferenceChangeListener(this);throw error;}
            if(immediately)item.publish(item.previous);
        }
    }
    void remove(OnAccountsUpdateListener listener){
        if(listener==null)throw new IllegalArgumentException("listener");
        synchronized(manager){
            Registration item=listeners.remove(listener);
            if(item!=null){item.active=false;item.pending=null;item.handler.removeCallbacks(item.delivery);}
            if(listeners.isEmpty())store.unregisterOnSharedPreferenceChangeListener(this);
        }
    }
    public void onSharedPreferenceChanged(SharedPreferences preferences,String key){
        if(!"accounts".equals(key))return;
        synchronized(manager){
            if(listeners.isEmpty())return;
            Account[] accounts=manager.getAccounts();
            for(Registration item:listeners.values()){
                Account[] selected=item.select(accounts);
                if(!Arrays.equals(selected,item.previous)){item.previous=selected;item.publish(selected);}
            }
        }
    }
}
