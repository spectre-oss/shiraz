// SPDX-License-Identifier: GPL-3.0-only
import android.accounts.*;
import android.content.*;
import android.os.*;
import java.nio.file.*;
import java.util.*;
import shiraz.runtime.HostPreferences;
public class AccountsSmoke {
    static class TestContext extends ContextWrapper {
        final HostPreferences preferences;
        TestContext(HostPreferences p){super(null);preferences=p;}
        public Context getApplicationContext(){return this;}
        public String getPackageName(){return "test.local.accounts";}
        public SharedPreferences getSharedPreferences(String name,int mode){return preferences.get(name,mode);}
    }
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void drain(){for(int i=0;i<4;i++)Looper.getMainLooper().shirazDispatchDue(100);}
    static void verifyListeners(AccountManager manager,AccountManager writer){
        final ArrayList<Account[]> seen=new ArrayList<Account[]>();final int[] unrelated={0};
        OnAccountsUpdateListener listener=new OnAccountsUpdateListener(){public void onAccountsUpdated(Account[] accounts){check(Looper.myLooper()==Looper.getMainLooper());seen.add(accounts);}};
        OnAccountsUpdateListener other=new OnAccountsUpdateListener(){public void onAccountsUpdated(Account[] accounts){unrelated[0]++;}};
        Account account=new Account("listener-local","test.type");
        try{
            try{manager.addOnAccountsUpdatedListener(null,null,true);throw new AssertionError();}catch(IllegalArgumentException expected){}
            String[] types={account.type};manager.addOnAccountsUpdatedListener(listener,null,true,types);types[0]="changed";
            check(seen.isEmpty());drain();check(seen.size()==1&&seen.get(0).length==0);
            try{manager.addOnAccountsUpdatedListener(listener,null,false);throw new AssertionError();}catch(IllegalStateException expected){}
            manager.addOnAccountsUpdatedListener(other,null,false,new String[]{"other.type"});
            check(writer.addAccountExplicitly(account,null,null));drain();check(seen.size()==2&&seen.get(1).length==1&&account.equals(seen.get(1)[0]));
            seen.get(1)[0]=null;check(account.equals(writer.getAccounts()[0]));
            check(!writer.addAccountExplicitly(account,null,null));writer.setAuthToken(account,"test","synthetic");writer.setUserData(account,"label","value");drain();check(seen.size()==2&&unrelated[0]==0);
            check(writer.removeAccountExplicitly(account));drain();check(seen.size()==3&&seen.get(2).length==0);
            check(writer.addAccountExplicitly(account,null,null));check(writer.removeAccountExplicitly(account));drain();check(seen.size()==4&&seen.get(3).length==0);
            manager.removeOnAccountsUpdatedListener(listener);
            manager.addOnAccountsUpdatedListener(listener,null,true);manager.removeOnAccountsUpdatedListener(listener);drain();check(seen.size()==4);
            manager.addOnAccountsUpdatedListener(listener,null,true,new String[0]);drain();check(seen.size()==5&&seen.get(4).length==0);
            System.out.println("LOCAL_ACCOUNT_LISTENERS_OK");
        }finally{manager.removeOnAccountsUpdatedListener(listener);manager.removeOnAccountsUpdatedListener(other);}
    }
    public static void main(String[] args)throws Exception{
        Looper.prepareMainLooper();Path root=Files.createTempDirectory(Paths.get("build"),"accounts-test-");
        Account account=new Account("test-user","test.type");
        try {
            try(HostPreferences preferences=new HostPreferences(root.toFile())){
                AccountManager manager=AccountManager.get(new TestContext(preferences));
                check(manager.getAccounts().length==0);
                verifyListeners(manager,AccountManager.get(new TestContext(preferences)));
                Bundle extras=new Bundle();extras.putString("label","test-label");
                check(manager.addAccountExplicitly(account,null,extras));
                check(!manager.addAccountExplicitly(account,null,null));
                check(manager.getAccountsByType("test.type").length==1);
                check(manager.getAccountsByType("other").length==0);
                check("test-label".equals(manager.getUserData(account,"label")));
                manager.setUserData(account,"label","changed");
                manager.setAuthToken(account,"test-token-type","fixture-token");
                manager.invalidateAuthToken("other","fixture-token");
                check(manager.peekAuthToken(account,"test-token-type")!=null);
                manager.invalidateAuthToken("test.type","fixture-token");
                check(manager.peekAuthToken(account,"test-token-type")==null);
            }
            try(HostPreferences preferences=new HostPreferences(root.toFile())){
                AccountManager manager=AccountManager.get(new TestContext(preferences));
                check(manager.getAccounts()[0].equals(account));
                check("changed".equals(manager.getUserData(account,"label")));
                check(manager.removeAccountExplicitly(account));
                check(!manager.removeAccountExplicitly(account));
                check(manager.getAccounts().length==0);
            }
            System.out.println("ACCOUNTS_PERSISTENCE_OK");
        } finally {
            Looper.shirazShutdownMain();
            try(java.util.stream.Stream<Path> paths=Files.walk(root)){
                Iterator<Path> ordered=paths.sorted(Comparator.reverseOrder()).iterator();
                while(ordered.hasNext())Files.delete(ordered.next());
            }
        }
    }
}
