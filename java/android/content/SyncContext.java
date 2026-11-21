// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;

/** Adapter-side completion and rate-limited progress notifications. */
public class SyncContext {
    private final ISyncContext callback;
    private long lastHeartbeat;
    public SyncContext(ISyncContext callback){this.callback=callback;}
    public synchronized void setStatusText(String message){
        long now=SystemClock.elapsedRealtime();
        if(now-lastHeartbeat<1000)return;
        lastHeartbeat=now;
        try{if(callback!=null)callback.sendHeartbeat();}catch(RemoteException unavailable){/* The coordinator has gone away. */}
    }
    public void onFinished(SyncResult result){
        try{if(callback!=null)callback.onFinished(result);}catch(RemoteException unavailable){/* Completion cannot be redelivered safely here. */}
    }
    public IBinder getSyncContextBinder(){return callback==null?null:callback.asBinder();}
}
