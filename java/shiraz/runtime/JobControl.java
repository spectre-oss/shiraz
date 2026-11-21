// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.app.job.JobParameters;
import android.app.job.JobWorkItem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.IntConsumer;

/** Private job channel: one reader multiplexes stop messages and work replies. */
final class JobControl implements AdmittedJob.Control,AutoCloseable,JobParameters.Work {
 private final int channel;private final Object requests=new Object();
 private IntConsumer listener;private JobParameters params;
 private boolean closed,detached,pending,drained;
 private IOException failure;private byte[] response;
 private Thread reader;private int stopReason;
 JobControl(int channel){this.channel=channel;}
 public synchronized void attach(IntConsumer stop){
  if(reader!=null||closed)throw new IllegalStateException("Job control already used");listener=stop;
  reader=new Thread(new Runnable(){public void run(){receive();}},"shiraz-job-control");reader.setDaemon(true);reader.start();
 }
 public synchronized void bindWork(JobParameters value){if(params!=null||closed||detached)throw new IllegalStateException("Job work already attached");params=value;value.shirazWork(this);}
 private void receive(){
  try{while(true){try(NativeIpc.Packet packet=NativeIpc.receive(channel)){
   if(packet.descriptorCount()!=0)throw new IOException("Job reply attachments unsupported");
   IntConsumer stop=null;int reason=0;
   synchronized(this){
    if(closed)return;
    if(Arrays.equals(packet.bytes,"{\"stop_reason\":9}".getBytes(StandardCharsets.UTF_8))){stop=listener;reason=9;}
    else if(Arrays.equals(packet.bytes,"{\"stop_reason\":14}".getBytes(StandardCharsets.UTF_8))){stop=listener;reason=14;}
    else {if(!pending||response!=null)throw new IOException("Unsolicited job work reply");response=packet.bytes;notifyAll();}
   }
   if(stop!=null)stop.accept(reason);
  }}}catch(IOException error){fail(error);}
 }
 private void fail(IOException error){IntConsumer stop;synchronized(this){if(closed)return;if(failure==null)failure=error;stop=listener;notifyAll();}if(stop!=null)stop.accept(14);}
 private JobWorkReply request(JobParameters value,String request,boolean completing){
  synchronized(requests){
   try{
    synchronized(this){if(value!=params||closed||detached||reader==null)throw new IllegalStateException("Inactive job work callback");if(failure!=null)throw failure;pending=true;response=null;}
    NativeIpc.send(channel,request.getBytes(StandardCharsets.UTF_8),new int[0]);
    long deadline=System.nanoTime()+10000000000L;byte[] reply;
    synchronized(this){
     while(response==null&&failure==null&&!closed&&!detached){long remaining=deadline-System.nanoTime();if(remaining<=0)throw new IOException("Job work reply timed out");try{wait(Math.max(1,remaining/1000000));}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("Job work request interrupted",error);}}
     if(failure!=null)throw failure;if(response==null)throw new IOException("Job work channel closed");reply=response;response=null;pending=false;
    }
    return JobWorkReply.decode(reply,completing);
   }catch(IOException error){fail(error);throw new IllegalStateException("Job work outcome unavailable",error);}
  }
 }
 public JobWorkItem dequeue(JobParameters value){synchronized(requests){synchronized(this){if(value!=params||closed||detached)throw new IllegalStateException("Inactive job work callback");if(drained)return null;}JobWorkReply reply=request(value,"{\"op\":\"work_dequeue\"}",false);if(reply.drained){synchronized(this){drained=true;}value.shirazFinish(false);}return reply.item;}}
 public boolean complete(JobParameters value,int id){synchronized(requests){synchronized(this){if(value!=params||closed||detached)throw new IllegalStateException("Inactive job work callback");if(drained)return false;}JobWorkReply reply=request(value,"{\"op\":\"work_complete\",\"id\":"+id+"}",true);if(reply.drained){synchronized(this){drained=true;}value.shirazFinish(false);}return reply.completed;}}
 public synchronized void detach(){listener=null;detached=true;notifyAll();}
 public synchronized void stopped(int reason){stopReason=reason;}
 synchronized int stopReason(){return stopReason;}
 synchronized void check()throws IOException{if(failure!=null)throw failure;}
 public void close(){
  Thread thread;synchronized(this){closed=true;listener=null;notifyAll();thread=reader;}
  NativeIpc.shutdown(channel);
  if(thread!=null)try{thread.join(2000);if(thread.isAlive())throw new IllegalStateException("Job control reader did not stop");}
  catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("Job control close interrupted",error);}
 }
}
