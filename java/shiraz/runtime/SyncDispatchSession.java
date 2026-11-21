// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.SyncResult;
import android.os.*;
import android.util.JsonWriter;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Dedicated host-owned result/cancel channel for one locally executing sync. */
final class SyncDispatchSession implements Closeable {
    private final ParcelFileDescriptor channel;
    private final Handler main;
    private final Runnable cleanup;
    private final LocalSyncExecution execution;
    private boolean closed;
    SyncDispatchSession(ParcelFileDescriptor channel,IBinder adapter,SyncDispatchRequest request,Handler main,Runnable cleanup){
        this.channel=channel;this.main=main;this.cleanup=cleanup;
        execution=new LocalSyncExecution(adapter,request.account,request.authority,request.extras,new LocalSyncExecution.Listener(){
            public void finished(final SyncResult result,final boolean canceled){
                if(!SyncDispatchSession.this.main.post(new Runnable(){public void run(){complete(result,canceled);}}))
                    System.err.println("SHIRAZ_SYNC_COMPLETION_QUEUE_CLOSED");
            }
        });
    }
    void start(){
        Thread watcher=new Thread(new Runnable(){public void run(){
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0||packet.bytes.length!=1||packet.bytes[0]!=1)throw new IOException("Invalid sync cancellation");
            }catch(IOException failure){/* Host channel loss also requests cancellation. */}
            main.post(new Runnable(){public void run(){cancel();}});
        }},"shiraz-sync-cancel");watcher.setDaemon(true);watcher.start();
        try{execution.start();}
        catch(RemoteException|RuntimeException failure){
            try{NativeIpc.send(channel.getFd(),"{\"ok\":false,\"delivery_uncertain\":true}".getBytes(StandardCharsets.UTF_8),new int[0]);}
            catch(IOException lost){System.err.println("SHIRAZ_SYNC_DELIVERY_REPORT_LOST");}
            // Keep service binding until the host confirms process termination.
        }
    }
    private synchronized void cancel(){
        if(closed)return;
        try{execution.cancel();}catch(RemoteException|RuntimeException failure){System.err.println("SHIRAZ_SYNC_CANCEL_FAILED");}
    }
    private synchronized void complete(SyncResult result,boolean canceled){
        if(closed)return;
        try{
            StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
            writer.beginObject().name("ok").value(true).name("canceled").value(canceled)
                .name("hard_error").value(result.hasHardError()).name("soft_error").value(result.hasSoftError())
                .name("full_sync_requested").value(result.fullSyncRequested).name("more_records").value(result.moreRecordsToGet)
                .name("partial_sync_unavailable").value(result.partialSyncUnavailable).name("delay_until").value(result.delayUntil)
                .name("already_in_progress").value(result.syncAlreadyInProgress).endObject();writer.close();
            cleanup.run();
            NativeIpc.send(channel.getFd(),text.toString().getBytes(StandardCharsets.UTF_8),new int[0]);
        }catch(IOException|RuntimeException failure){System.err.println("SHIRAZ_SYNC_COMPLETION_FAILED");}
        finally{closed=true;NativeIpc.shutdown(channel.getFd());try{channel.close();}catch(IOException ignored){}}
    }
    public synchronized void close()throws IOException{
        if(closed)return;cancel();closed=true;NativeIpc.shutdown(channel.getFd());channel.close();
        cleanup.run();
    }
}
