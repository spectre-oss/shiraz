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
        public SharedPreferences getSharedPreferences(String name,int mode){return preferences.get(name,mode);}
    }
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        Looper.prepareMainLooper();Path root=Files.createTempDirectory(Paths.get("build"),"accounts-test-");
        Account account=new Account("test-user","test.type");
        try {
            try(HostPreferences preferences=new HostPreferences(root.toFile())){
                AccountManager manager=AccountManager.get(new TestContext(preferences));
                check(manager.getAccounts().length==0);
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
                for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);
            }
        }
    }
}
