// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.content.Intent;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;

/** Single admitted launch over a broker-owned, caller-UID-pinned Binder channel. */
public final class ActivityLaunchEndpoint extends Binder implements Closeable,IBinder.DeathRecipient {
    private static final String DESCRIPTOR="shiraz.runtime.IActivityLaunch";
    private final String packageName,component;
    private final int callerUid;
    private final CompletableFuture<Intent> launch=new CompletableFuture<Intent>();
    private boolean closed,accepted;
    public ActivityLaunchEndpoint(String pkg,String component,int callerUid){
        if(pkg==null||component==null||callerUid<10000||callerUid>19999)throw new IllegalArgumentException("Admitted activity identity required");
        packageName=pkg;this.component=component;this.callerUid=callerUid;attachInterface(null,DESCRIPTOR);
    }
    @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
        if(code==INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(DESCRIPTOR);return true;}
        if(code!=1)return false;
        if(flags!=0||reply==null)throw new IllegalArgumentException("Synchronous activity admission required");
        if(Binder.getCallingUid()!=callerUid)throw new SecurityException("Activity caller mismatch");
        if(data.dataSize()>4*1024*1024)throw new IllegalArgumentException("Activity launch payload limit");
        data.enforceInterface(DESCRIPTOR);Intent intent=data.readTypedObject(Intent.CREATOR);data.enforceNoDataAvail();
        if(intent==null||intent.getComponent()==null||!packageName.equals(intent.getComponent().getPackageName())||!component.equals(intent.getComponent().getClassName())||intent.getPackage()!=null&&!packageName.equals(intent.getPackage()))throw new SecurityException("Activity launch target mismatch");
        if(intent.getSelector()!=null||intent.getClipData()!=null||(intent.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))!=0||intent.getExtras()!=null&&intent.getExtras().hasFileDescriptors())throw new UnsupportedOperationException("Activity selector or URI/descriptor grants are not implemented");
        Intent snapshot=intent.shirazBroadcastCopy();
        synchronized(this){if(closed||accepted)throw new IllegalStateException("Activity launch already consumed or closed");accepted=true;launch.complete(snapshot);}
        reply.writeNoException();return true;
    }
    public Intent await(long timeout,TimeUnit unit)throws IOException{
        try{return launch.get(timeout,unit);}
        catch(InterruptedException failure){Thread.currentThread().interrupt();close();throw new IOException("Activity launch interrupted",failure);}
        catch(TimeoutException failure){close();throw new IOException("Activity launch deadline expired",failure);}
        catch(ExecutionException failure){throw new IOException("Activity launch channel closed",failure.getCause());}
    }
    public void binderDied(){close();}
    public synchronized void close(){closed=true;launch.completeExceptionally(new IOException("Activity launch closed"));}
    public static void send(IBinder target,Intent intent)throws RemoteException{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{data.writeInterfaceToken(DESCRIPTOR);data.writeTypedObject(intent,0);if(!target.transact(1,data,reply,0))throw new RemoteException("Activity admission unavailable");reply.readException();reply.enforceNoDataAvail();}
        finally{data.recycle();reply.recycle();}
    }
}
