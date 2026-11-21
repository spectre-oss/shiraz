// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.Service;
import android.accounts.Account;
import android.content.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class SyncDispatchSessionSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void string(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    static byte[] frame()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(3);
        string(out,new String(new char[64]).replace('\0','a'));string(out,"test.sync");string(out,"test.sync.Adapter");string(out,"fixture.data");string(out,"fixture-user");string(out,"fixture.accounts");
        out.writeInt(2);string(out,"large");out.writeByte(3);out.writeLong(Long.MAX_VALUE);string(out,"empty");out.writeByte(6);string(out,"");out.close();return bytes.toByteArray();
    }
    static final class Fixture extends Service {
        int binds,unbinds;
        public IBinder onBind(Intent intent){binds++;check("android.content.SyncAdapter".equals(intent.getAction()),"bind action");return new ISyncAdapter.Stub(){
            public void startSync(final ISyncContext callback,String authority,Account account,Bundle extras){
                check(authority.equals("fixture.data")&&account.name.equals("fixture-user"),"dispatch arguments");
                check(extras.getLong("large")==Long.MAX_VALUE&&"".equals(extras.getString("empty")),"typed extras");
                Thread worker=new Thread(new Runnable(){public void run(){try{SyncResult result=new SyncResult();result.stats.numUpdates=1;callback.onFinished(result);}catch(RemoteException error){throw new RuntimeException(error);}}});worker.start();
            }
            public void cancelSync(ISyncContext callback){}
        };}
        public boolean onUnbind(Intent intent){unbinds++;return false;}
    }
    public static void main(String[] args)throws Exception{
        Looper.prepareMainLooper();final Handler main=new Handler(Looper.getMainLooper());
        int[] pair=NativeIpc.pair();final ParcelFileDescriptor peer=ParcelFileDescriptor.adoptFd(pair[0]);
        final Fixture fixture=new Fixture();final SharedServiceHost host=new SharedServiceHost(pair[1],fixture,null,"test.sync","test.sync.Adapter");
        final AtomicReference<Throwable> failure=new AtomicReference<Throwable>();
        Thread sender=new Thread(new Runnable(){public void run(){
            int[] channel=null;
            try{
                byte[] payload=frame();SyncDispatchRequest.decode(payload,"test.sync","test.sync.Adapter");
                try{SyncDispatchRequest.decode(payload,"test.other","test.sync.Adapter");throw new AssertionError("wrong target accepted");}catch(IOException expected){}
                try{SyncDispatchRequest.decode(java.util.Arrays.copyOf(payload,payload.length-1),"test.sync","test.sync.Adapter");throw new AssertionError("truncation accepted");}catch(IOException expected){}
                channel=NativeIpc.pair();
                try(ParcelFileDescriptor worker=ParcelFileDescriptor.adoptFd(channel[1]);ParcelFileDescriptor result=ParcelFileDescriptor.adoptFd(channel[0])){
                    NativeIpc.send(peer.getFd(),payload,new int[]{worker.getFd()});
                    try(NativeIpc.Packet ack=NativeIpc.receive(peer.getFd())){check(new String(ack.bytes,StandardCharsets.UTF_8).equals("{\"sync_admitted\":true}"),"dispatch ack");}
                    try(NativeIpc.Packet response=NativeIpc.receive(result.getFd())){
                        String json=new String(response.bytes,StandardCharsets.UTF_8);
                        check(json.contains("\"ok\":true")&&json.contains("\"hard_error\":false")&&json.contains("\"canceled\":false"),"sync result");
                    }
                }
            }catch(Throwable error){failure.set(error);}
            finally{main.post(new Runnable(){public void run(){try{host.close();peer.close();}catch(Throwable error){failure.set(error);}finally{Looper.shirazRequestMainExit();}}});}
        }},"fixture-sync-host");sender.start();Looper.loop();sender.join(5000);
        check(!sender.isAlive(),"host thread retained");if(failure.get()!=null)throw new AssertionError("dispatch failed",failure.get());
        check(fixture.binds==1&&fixture.unbinds==1,"binding lifecycle");
        System.out.println("SYNC_DISPATCH_SESSION_OK");
    }
}
