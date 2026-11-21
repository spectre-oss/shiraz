// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.app.job.JobParameters;
import android.app.job.JobWorkItem;
import android.content.Intent;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class JobWorkChannelSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void send(int fd,String value)throws Exception{NativeIpc.send(fd,value.getBytes(StandardCharsets.UTF_8),new int[0]);}
 static String read(int fd)throws Exception{try(NativeIpc.Packet packet=NativeIpc.receive(fd)){check(packet.descriptorCount()==0,"no attachments");return new String(packet.bytes,StandardCharsets.UTF_8);}}
 public static void main(String[] args)throws Exception{
  Parcel parcel=Parcel.obtain();new Intent("test.work").putExtra("sequence",41).writeToParcel(parcel,0);
  final String payload=java.util.Base64.getEncoder().encodeToString(parcel.marshall());parcel.recycle();
  for(String invalid:new String[]{"{\"ok\":true,\"ok\":true,\"item\":null,\"drained\":true}","{\"ok\":true,\"item\":null}","{\"ok\":true,\"item\":{\"id\":1,\"deliveries\":0,\"payload\":\"AA==\"},\"drained\":false}"}){
   try{JobWorkReply.decode(invalid.getBytes(StandardCharsets.UTF_8),false);throw new AssertionError("Malformed reply accepted");}catch(java.io.IOException expected){}
  }
  final int[] pair=NativeIpc.pair();final CountDownLatch release=new CountDownLatch(1);
  final AtomicReference<Throwable> failure=new AtomicReference<Throwable>();final AtomicInteger stops=new AtomicInteger(),finishes=new AtomicInteger();
  try(ParcelFileDescriptor client=ParcelFileDescriptor.adoptFd(pair[0]);final ParcelFileDescriptor peer=ParcelFileDescriptor.adoptFd(pair[1])){
   final JobControl control=new JobControl(client.getFd());
   Thread server=new Thread(new Runnable(){public void run(){try{
    check(read(peer.getFd()).equals("{\"op\":\"work_dequeue\"}"),"dequeue request");
    send(peer.getFd(),"{\"stop_reason\":9}");
    send(peer.getFd(),"{\"stop_reason\":14}");
    send(peer.getFd(),"{\"ok\":true,\"item\":{\"id\":7,\"deliveries\":2,\"payload\":\""+payload+"\"},\"drained\":false}");
    check(read(peer.getFd()).equals("{\"op\":\"work_complete\",\"id\":7}"),"completion request");
    send(peer.getFd(),"{\"ok\":true,\"completed\":true,\"drained\":true}");
    check(release.await(5,TimeUnit.SECONDS),"release wait");
   }catch(Throwable error){failure.set(error);NativeIpc.shutdown(peer.getFd());}}});server.start();
   JobParameters params=new JobParameters(1,new PersistableBundle());
   params.shirazCompletion(new JobParameters.Completion(){public void finish(JobParameters p,boolean retry){check(!retry,"drained success");finishes.incrementAndGet();}});
   try{
    control.attach(new java.util.function.IntConsumer(){public void accept(int reason){check(reason==9||reason==14,"cooperative stop");stops.incrementAndGet();}});control.bindWork(params);
    JobWorkItem item=params.dequeueWork();
    check(item.getWorkId()==7&&item.getDeliveryCount()==2&&item.getIntent().getIntExtra("sequence",0)==41,"delivered work");
    check(stops.get()==2&&finishes.get()==0,"stop interleaved without stealing response");
    params.completeWork(item);check(finishes.get()==1,"drained acknowledgement finishes job");check(params.dequeueWork()==null,"drained loop ends locally");control.check();control.detach();
    try{params.dequeueWork();throw new AssertionError("detached callback accepted");}catch(IllegalStateException expected){}
   }finally{control.close();release.countDown();server.join(5000);}
   check(!server.isAlive(),"server stopped");if(failure.get()!=null)throw new AssertionError(failure.get());
  }
  System.out.println("JOB_WORK_CHANNEL_OK");
 }
}
