// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.ContentResolver;
import android.content.SyncAdapterType;

/** Uses the live admitted broker client; reflection only installs the test process owner. */
public final class SyncDiscoveryApiSmoke {
    public static void run(ServiceBrokerClient client)throws Exception{
        java.lang.reflect.Field field=RuntimeBroker.class.getDeclaredField("client");field.setAccessible(true);
        Object previous=field.get(null);
        try{
            field.set(null,null);
            try{ContentResolver.getSyncAdapterTypes();throw new AssertionError("Unregistered discovery fabricated a result");}
            catch(UnsupportedOperationException expected){}
            field.set(null,client);
            SyncAdapterType[] first=ContentResolver.getSyncAdapterTypes();
            if(first.length!=1||!first[0].authority.equals("test.data")||!first[0].accountType.equals("test.accounts")||!first[0].getPackageName().equals("test.sync")||first[0].isUserVisible()||!first[0].supportsUploading())throw new AssertionError("Selected sync API value");
            first[0]=null;
            SyncAdapterType[] second=ContentResolver.getSyncAdapterTypes();
            if(second==first||second.length!=1||second[0]==null)throw new AssertionError("Discovery array reused");
            System.out.println("ART_SYNC_DISCOVERY_API_OK");
        }finally{field.set(null,previous);}
    }
}
