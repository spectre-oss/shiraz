// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.HostDownloads;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class DownloadCancelBlockedSmoke {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void stalled(final int stage,final boolean closeQueue)throws Exception{
  File root=Files.createTempDirectory("download-cancel-blocked").toFile(),state=new File(root,"queue"),target=new File(root,"blocked.bin");
  CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger completed=new AtomicInteger();
  ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"));
  Thread response=new Thread(new Runnable(){public void run(){try{try(Socket socket=server.accept()){
   if(stage==2){check(socket.getInputStream().read()==22,"Expected TLS handshake record");}
   else{BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));while(!input.readLine().isEmpty()){}
    if(stage==1){socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\n".getBytes("UTF-8"));socket.getOutputStream().flush();}
   }
   started.countDown();release.await(20,TimeUnit.SECONDS);
  }
  if(!closeQueue)try(Socket socket=server.accept()){
   BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));while(!input.readLine().isEmpty()){}
   socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nnext".getBytes("UTF-8"));
  }
  }catch(Exception error){if(!server.isClosed())throw new RuntimeException(error);}}});response.setDaemon(true);response.start();
  HostDownloads queue=new HostDownloads(state,root,new HostDownloads.Listener(){public void finished(HostDownloads.Record record){completed.incrementAndGet();}});
  try{
   long id=queue.enqueue((stage==2?"https":"http")+"://127.0.0.1:"+server.getLocalPort()+"/blocked",target,"","",Collections.<String,String>emptyMap());
   check(started.await(5,TimeUnit.SECONDS),"request did not reach server");Thread.sleep(100);
   long pending=queue.enqueue("http://127.0.0.1:"+server.getLocalPort()+"/pending",new File(root,"pending.bin"),"","",Collections.<String,String>emptyMap());
   check(queue.remove(pending),"pending removal rejected");Thread.sleep(350);
   check(queue.get(id).status==HostDownloads.RUNNING,"removing another request interrupted active transfer");
   long start=System.nanoTime();
   if(closeQueue)queue.close();
   else{check(queue.remove(id),"cancellation rejected");check(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(500),"remove blocked application caller");}
   long end=start+TimeUnit.SECONDS.toNanos(3);
   if(closeQueue){check(queue.get(id).status==HostDownloads.PENDING,"shutdown lost restartable request");}
   else{
    while(new File(state,id+".properties").exists()&&System.nanoTime()<end)Thread.sleep(20);
    check(!new File(state,id+".properties").exists(),"blocked stage "+stage+" prevented cancellation cleanup");
   }
   check(System.nanoTime()<end,"cancellation blocked caller");
   check(!target.exists()&&completed.get()==0,"cancelled transfer completed");
   if(!closeQueue){
    release.countDown();File nextFile=new File(root,"next.bin");
    long next=queue.enqueue("http://127.0.0.1:"+server.getLocalPort()+"/next",nextFile,"","",Collections.<String,String>emptyMap());
    long nextDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
    while(queue.get(next).status!=HostDownloads.SUCCESSFUL&&System.nanoTime()<nextDeadline)Thread.sleep(20);
    check(queue.get(next).status==HostDownloads.SUCCESSFUL&&Arrays.equals(Files.readAllBytes(nextFile.toPath()),"next".getBytes("UTF-8")),"queue did not advance after cancellation");
   }
  }finally{release.countDown();queue.close();server.close();response.join(2000);}
  for(Thread thread:Thread.getAllStackTraces().keySet())check(!thread.isAlive()||!thread.getName().equals("Shiraz download cancellation"),"cancellation watcher leaked after close");
  System.out.println("DOWNLOAD_CANCEL_STAGE_OK "+stage+" close="+closeQueue);
 }
 public static void main(String[] args)throws Exception{
  if(java.security.Security.getProvider("wolfJSSE")!=null)shiraz.runtime.HostTlsProvider.install();
  for(boolean closeQueue:new boolean[]{false,true}){stalled(1,closeQueue);stalled(0,closeQueue);stalled(2,closeQueue);}
  System.out.println("DOWNLOAD_CANCEL_BLOCKED_OK");
 }
}
