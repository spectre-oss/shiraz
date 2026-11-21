// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.accounts.Account;
import android.accounts.OnAccountsUpdateListener;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** One authenticated watch per process, shared by AccountManager instances. */
public final class HostAccountListeners {
    private static final Object lock=new Object();
    private static final ArrayList<Registration> registrations=new ArrayList<Registration>();
    private static ServiceBrokerClient.AccountWatch watch;
    private HostAccountListeners(){}
    private static final class Registration {
        final Object owner;final OnAccountsUpdateListener listener;final Handler handler;
        final Set<String> types;Account[] previous,pending;boolean queued;volatile boolean active=true;
        final Runnable delivery=new Runnable(){public void run(){
            Account[] accounts;
            synchronized(lock){queued=false;accounts=pending;pending=null;if(!active)return;}
            listener.onAccountsUpdated(accounts.clone());
        }};
        Registration(Object owner,OnAccountsUpdateListener listener,Handler handler,String[] types){
            this.owner=owner;this.listener=listener;this.handler=handler;
            this.types=types==null?null:new HashSet<String>(Arrays.asList(types.clone()));
        }
        Account[] select(Account[] accounts){
            ArrayList<Account> selected=new ArrayList<Account>();
            for(Account account:accounts)if(types==null||types.contains(account.type))selected.add(account);
            return selected.toArray(new Account[selected.size()]);
        }
        void publish(final Account[] accounts){
            pending=accounts;
            if(!queued){queued=true;if(!handler.post(delivery)){queued=false;pending=null;}}
        }
        void deactivate(){
            active=false;pending=null;handler.removeCallbacks(delivery);
        }
    }
    public static void add(Object owner,OnAccountsUpdateListener listener,Handler handler,boolean immediately,String[] types){
        if(listener==null)throw new IllegalArgumentException("listener");
        Handler target=handler==null?new Handler(Looper.getMainLooper()):handler;
        synchronized(lock){
            for(Registration item:registrations)if(item.owner==owner&&item.listener==listener)throw new IllegalStateException("Account listener already registered");
            if(registrations.size()>=1024)throw new IllegalStateException("Account listener limit");
            boolean created=false;
            try{
                if(watch==null){watch=HostAccounts.watch();created=true;}
                Account[] accounts=HostAccounts.snapshot().accounts();
                Registration item=new Registration(owner,listener,target,types);item.previous=item.select(accounts);
                registrations.add(item);
                if(created){
                    final ServiceBrokerClient.AccountWatch channel=watch;
                    Thread worker=new Thread(new Runnable(){public void run(){observe(channel);}},"account-listeners");
                    worker.setDaemon(true);
                    try{worker.start();}catch(RuntimeException|Error failure){registrations.remove(item);item.deactivate();throw failure;}
                }
                if(immediately)item.publish(item.previous);
            }catch(IOException failure){if(created){watch.close();watch=null;}throw new IllegalStateException("Account listener admission failed",failure);}
            catch(RuntimeException|Error failure){if(created&&watch!=null){watch.close();watch=null;}throw failure;}
        }
    }
    public static void remove(Object owner,OnAccountsUpdateListener listener){
        if(listener==null)throw new IllegalArgumentException("listener");
        synchronized(lock){
            for(int i=0;i<registrations.size();i++){
                Registration item=registrations.get(i);
                if(item.owner==owner&&item.listener==listener){item.deactivate();registrations.remove(i);break;}
            }
            if(registrations.isEmpty()&&watch!=null){watch.close();watch=null;}
        }
    }
    private static void observe(ServiceBrokerClient.AccountWatch channel){
        try{
            while(true){
                channel.readRevision();
                synchronized(lock){
                    if(watch!=channel)return;
                    Account[] accounts=HostAccounts.snapshot().accounts();
                    for(Registration item:registrations){
                        Account[] selected=item.select(accounts);
                        if(!Arrays.equals(item.previous,selected)){item.previous=selected;item.publish(selected);}
                    }
                }
            }
        }catch(IOException|RuntimeException failure){synchronized(lock){if(watch==channel)System.err.println("SHIRAZ_ACCOUNT_LISTENERS_DISCONNECTED");}}
        finally{
            synchronized(lock){
                if(watch==channel){for(Registration item:registrations)item.deactivate();registrations.clear();watch=null;}
                channel.close();
            }
        }
    }
    static void shutdown(){
        synchronized(lock){
            for(Registration item:registrations)item.deactivate();
            registrations.clear();if(watch!=null){watch.close();watch=null;}
        }
    }
}
