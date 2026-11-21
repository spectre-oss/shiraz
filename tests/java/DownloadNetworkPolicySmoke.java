// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.net.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
public final class DownloadNetworkPolicySmoke {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static class Source implements DownloadNetworkPolicy.Source {volatile NetworkCapabilities value;public NetworkCapabilities read(){return value;}}
 static HostDownloads.Record waitStatus(HostDownloads queue,long id,int wanted)throws Exception{
  long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
  while(System.nanoTime()<end){HostDownloads.Record r=queue.get(id);if(r!=null&&r.status==wanted)return r;Thread.sleep(20);}
  throw new AssertionError("Missing download status "+wanted);
 }
 static void interruptedTransfer()throws Exception{
  final File root=Files.createTempDirectory("download-policy-loss").toFile(),target=new File(root,"map.bin");
  final AtomicBoolean lost=new AtomicBoolean(),restored=new AtomicBoolean();
  final AtomicInteger requests=new AtomicInteger(),finished=new AtomicInteger();
  DownloadNetworkPolicy.Source source=new DownloadNetworkPolicy.Source(){public NetworkCapabilities read(){
   File[] files=root.listFiles();if(files!=null)for(File file:files)if(file.getName().endsWith(".part")&&file.length()>0)lost.set(true);
   return lost.get()&&!restored.get()?null:NetworkCapabilities.shirazHost(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
  }};
  final ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"));
  Thread response=new Thread(new Runnable(){public void run(){try{for(int i=0;i<2;i++)try(Socket socket=server.accept()){
   requests.incrementAndGet();BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));while(!input.readLine().isEmpty()){}
   socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ntest".getBytes("UTF-8"));
  }}catch(IOException e){throw new RuntimeException(e);}}});response.setDaemon(true);response.start();
  try(HostDownloads queue=new HostDownloads(new File(root,"queue"),root,new HostDownloads.Listener(){public void finished(HostDownloads.Record r){finished.incrementAndGet();}},source)){
   long id=queue.enqueue("http://127.0.0.1:"+server.getLocalPort()+"/map",target,"","",Collections.<String,String>emptyMap(),"",2,new DownloadNetworkPolicy(2,true,true));
   check(waitStatus(queue,id,HostDownloads.PAUSED).reason==2,"lost network reason");
   check(lost.get()&&!target.exists()&&requests.get()==1&&finished.get()==0,"lost policy published or completed transfer");
   long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);boolean partial;
   do{partial=false;for(File file:root.listFiles())if(file.getName().endsWith(".part"))partial=true;if(partial)Thread.sleep(20);}while(partial&&System.nanoTime()<end);
   check(!partial,"paused partial not cleaned");restored.set(true);
   waitStatus(queue,id,HostDownloads.SUCCESSFUL);response.join(2000);
   check(requests.get()==2&&Arrays.equals(Files.readAllBytes(target.toPath()),"test".getBytes("UTF-8")),"network recovery did not restart transfer");
  }finally{server.close();}
  check(finished.get()==1,"retried completion count");
 }
 static void failedReadPolicyLoss(final boolean loseNetwork)throws Exception{
  File root=Files.createTempDirectory("download-read-loss").toFile(),target=new File(root,"map.bin");
  final AtomicBoolean lost=new AtomicBoolean();final AtomicInteger reads=new AtomicInteger(),requests=new AtomicInteger(),finished=new AtomicInteger();
  final java.util.concurrent.CountDownLatch reading=new java.util.concurrent.CountDownLatch(1);
  DownloadNetworkPolicy.Source source=new DownloadNetworkPolicy.Source(){public NetworkCapabilities read(){
   if(reads.incrementAndGet()==3)reading.countDown();
   return lost.get()?null:NetworkCapabilities.shirazHost(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
  }};
  final ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"));
  Thread response=new Thread(new Runnable(){public void run(){try{for(int i=0;i<(loseNetwork?2:1);i++)try(Socket socket=server.accept()){
   requests.incrementAndGet();BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));while(!input.readLine().isEmpty()){}
   if(i==0){
    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n".getBytes("UTF-8"));socket.getOutputStream().flush();
    if(!reading.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("read not reached");
    lost.set(loseNetwork);socket.getOutputStream().write("invalid-chunk\r\n".getBytes("UTF-8"));
   }else socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ntest".getBytes("UTF-8"));
  }}catch(Exception e){throw new RuntimeException(e);}}});response.setDaemon(true);response.start();
  try(HostDownloads queue=new HostDownloads(new File(root,"queue"),root,new HostDownloads.Listener(){public void finished(HostDownloads.Record r){finished.incrementAndGet();}},source)){
   long id=queue.enqueue("http://127.0.0.1:"+server.getLocalPort()+"/map",target,"","",Collections.<String,String>emptyMap(),"",2,new DownloadNetworkPolicy(2,true,true));
   if(!loseNetwork){
    waitStatus(queue,id,HostDownloads.FAILED);response.join(2000);
    check(!target.exists()&&requests.get()==1,"malformed response retried with satisfied policy");
   }else{
   check(waitStatus(queue,id,HostDownloads.PAUSED).reason==2,"read failure with lost policy must pause");
   check(!target.exists()&&requests.get()==1&&finished.get()==0,"read failure emitted terminal result");
   lost.set(false);waitStatus(queue,id,HostDownloads.SUCCESSFUL);response.join(2000);
   check(requests.get()==2&&Arrays.equals(Files.readAllBytes(target.toPath()),"test".getBytes("UTF-8")),"read failure recovery");
   }
  }finally{server.close();}
  check(finished.get()==1,"read failure completion count");
 }
 public static void main(String[] args)throws Exception{
  if(java.security.Security.getProvider("wolfJSSE")!=null)HostTlsProvider.install();
  Source source=new Source();DownloadNetworkPolicy wifi=new DownloadNetworkPolicy(2,true,true);
  check(wifi.pauseReason(source)==2,"unknown network");
  source.value=NetworkCapabilities.shirazHost(NetworkCapabilities.TRANSPORT_WIFI);
  check(wifi.pauseReason(source)==3,"Wi-Fi name is not unmetered proof");
  source.value=NetworkCapabilities.shirazHost(NetworkCapabilities.TRANSPORT_ETHERNET).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
  check(wifi.pauseReason(source)==0,"Android N Wi-Fi flag must allow unmetered wired path");
  check(new DownloadNetworkPolicy(-1,true,false).pauseReason(source)==2,"unknown roaming");
  source.value.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
  check(new DownloadNetworkPolicy(-1,true,false).pauseReason(source)==0,"proven nonroaming");
  check(new DownloadNetworkPolicy(1,true,true).pauseReason(source)==2,"cellular restriction");
  check(new DownloadNetworkPolicy(0,true,true).pauseReason(source)==2,"empty mask");
  Properties p=new Properties();wifi.write(p);check(DownloadNetworkPolicy.read(p).pauseReason(source)==0,"policy round trip");
  p.setProperty("network.metered","bad");try{DownloadNetworkPolicy.read(p);throw new AssertionError("malformed policy accepted");}catch(IllegalArgumentException expected){}
  check(DownloadNetworkPolicy.read(new Properties()).pauseReason(source)==0,"legacy unrestricted record");
  File root=Files.createTempDirectory("download-policy").toFile(),dir=new File(root,"queue"),target=new File(root,"map.bin");
  AtomicInteger requests=new AtomicInteger(),finished=new AtomicInteger();source.value=null;
  ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));
  Thread response=new Thread(new Runnable(){public void run(){try(Socket socket=server.accept()){
   requests.incrementAndGet();BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));while(!input.readLine().isEmpty()){}
   socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ntest".getBytes("UTF-8"));
  }catch(IOException e){throw new RuntimeException(e);}}});response.setDaemon(true);response.start();
  HostDownloads.Listener listener=new HostDownloads.Listener(){public void finished(HostDownloads.Record r){finished.incrementAndGet();}};
  HostDownloads queue=new HostDownloads(dir,root,listener,source);
  try{
   long id=queue.enqueue("http://127.0.0.1:"+server.getLocalPort()+"/map",target,"map","",Collections.<String,String>emptyMap(),"",2,wifi);
   check(waitStatus(queue,id,HostDownloads.PAUSED).reason==2,"paused unknown reason");
   check(!target.exists()&&requests.get()==0&&finished.get()==0,"paused request performed transfer or completion");
   queue.close();queue=new HostDownloads(dir,root,listener,source);
   check(waitStatus(queue,id,HostDownloads.PAUSED).reason==2,"restart lost policy");
   source.value=NetworkCapabilities.shirazHost(NetworkCapabilities.TRANSPORT_WIFI);
   long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
   while(queue.get(id).reason!=3&&System.nanoTime()<end)Thread.sleep(20);
   check(queue.get(id).reason==3&&requests.get()==0,"unknown metering permitted transfer");
   source.value=new NetworkCapabilities(source.value).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
   waitStatus(queue,id,HostDownloads.SUCCESSFUL);response.join(2000);
   check(requests.get()==1&&Arrays.equals(Files.readAllBytes(target.toPath()),"test".getBytes("UTF-8")),"resumed transfer");
  }finally{queue.close();server.close();}
  check(finished.get()==1,"completion count");
  interruptedTransfer();
  failedReadPolicyLoss(true);failedReadPolicyLoss(false);
  System.out.println("DOWNLOAD_NETWORK_POLICY_OK");
 }
}
