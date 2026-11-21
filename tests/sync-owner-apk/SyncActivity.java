// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.syncownerfixture;
import android.accounts.Account;
import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;

/** SDK-built APK; never invokes Shiraz's explicit adapter cleanup hook. */
public final class SyncActivity extends Activity {
    private AbstractThreadedSyncAdapter adapter;
    protected void onCreate(Bundle state){
        super.onCreate(state);
        android.widget.TextView view=new android.widget.TextView(this);view.setText("Sync owner shutdown fixture");setContentView(view);
        adapter=start(getApplicationContext(),new Runnable(){public void run(){finish();}});
    }
    static AbstractThreadedSyncAdapter start(final Context context,final Runnable finished){
        final java.util.concurrent.atomic.AtomicInteger changes=new java.util.concurrent.atomic.AtomicInteger();
        final android.database.ContentObserver observer=new android.database.ContentObserver(null){
            public void onChange(boolean self,Uri uri){if(!self&&"content://org.shiraz.syncownerfixture.data/probe".equals(uri.toString()))changes.incrementAndGet();}
        };
        context.getContentResolver().registerContentObserver(Uri.parse("content://org.shiraz.syncownerfixture.data"),true,observer);
        final Cursor cursor=context.getContentResolver().query(Uri.parse("content://org.shiraz.syncownerfixture.data/probe"),null,null,null,null);
        final java.util.concurrent.atomic.AtomicInteger cursorChanges=new java.util.concurrent.atomic.AtomicInteger();
        cursor.registerContentObserver(new android.database.ContentObserver(null){public void onChange(boolean self){cursorChanges.incrementAndGet();}});
        AbstractThreadedSyncAdapter adapter=new AbstractThreadedSyncAdapter(context,false){
            public void onPerformSync(Account account,Bundle extras,String authority,ContentProviderClient provider,SyncResult result){
                try{if(provider.call("probe",null,null).getInt("value")!=9)throw new AssertionError("Provider not attached");}
                catch(RemoteException error){throw new AssertionError(error);}
                if(changes.get()!=1)throw new AssertionError("Provider content notification missing");
                if(cursorChanges.get()!=1||!cursor.moveToFirst()||cursor.getInt(0)!=9)throw new AssertionError("Cursor provider notification missing");
                cursor.close();
                System.out.println("SYNC_APK_CURSOR_OBSERVED");
                context.getContentResolver().unregisterContentObserver(observer);
                System.out.println("SYNC_APK_PROVIDER_OBSERVED");
                System.out.println("SYNC_APK_WORKER_ENTERED");
                if(finished!=null&&!new Handler(Looper.getMainLooper()).post(finished))throw new AssertionError("Activity finish not queued");
                try{Thread.sleep(60000);throw new AssertionError("Owner did not interrupt worker");}
                catch(InterruptedException expected){System.out.println("SYNC_APK_WORKER_INTERRUPTED");}
            }
        };
        Binder callback=new Binder(){protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){
            if(code==1||code==2){data.enforceInterface("android.content.ISyncContext");if(code==2)System.out.println("SYNC_APK_UNEXPECTED_COMPLETION");if(reply!=null)reply.writeNoException();return true;}return false;
        }};
        Parcel request=Parcel.obtain();
        try{
            request.writeInterfaceToken("android.content.ISyncAdapter");request.writeStrongBinder(callback);request.writeString("org.shiraz.syncownerfixture.data");
            request.writeInt(1);new Account("fixture","fixture.sync").writeToParcel(request,0);
            request.writeInt(1);new Bundle().writeToParcel(request,0);
            if(!adapter.getSyncAdapterBinder().transact(1,request,null,IBinder.FLAG_ONEWAY))throw new AssertionError("Start rejected");
        }catch(RemoteException error){throw new AssertionError(error);}finally{request.recycle();}
        return adapter;
    }
    protected void onDestroy(){super.onDestroy();System.out.println("SYNC_APK_ACTIVITY_DESTROYED");}
    public static final class Provider extends ContentProvider {
        public boolean onCreate(){return true;}
        public Bundle call(String method,String arg,Bundle extras){getContext().getContentResolver().notifyChange(Uri.parse("content://org.shiraz.syncownerfixture.data/probe"),null,false);Bundle value=new Bundle();value.putInt("value",9);return value;}
        public Cursor query(Uri u,String[] p,String s,String[] a,String o){android.database.MatrixCursor cursor=new android.database.MatrixCursor(new String[]{"value"});cursor.addRow(new Object[]{9});cursor.setNotificationUri(getContext().getContentResolver(),u);return cursor;}
        public String getType(Uri u){return null;}public Uri insert(Uri u,ContentValues v){return null;}
        public int delete(Uri u,String s,String[] a){return 0;}public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
    }
}
