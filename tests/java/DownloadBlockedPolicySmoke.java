// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.*;
import android.net.NetworkCapabilities;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class DownloadBlockedPolicySmoke {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void run(boolean disconnected)throws Exception{
  final NetworkCapabilities allowed=NetworkCapabilities.shirazHost(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
  final AtomicReference<NetworkCapabilities> observed=new AtomicReference<>(allowed);
  DownloadNetworkPolicy.Source source=new DownloadNetworkPolicy.Source(){public NetworkCapabilities read(){return observed.get();}};
  File root=Files.createTempDirectory("download-blocked-policy").toFile(),target=new File(root,"map.bin");
  CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger completed=new AtomicInteger(),requests=new AtomicInteger();
  ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"));
  Thread response=new Thread(new Runnable(){public void run(){try{for(int i=0;i<2;i++)try(Socket socket=server.accept()){
   requests.incrementAndGet();BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));while(!input.readLine().isEmpty()){}
   socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\n".getBytes("UTF-8"));socket.getOutputStream().flush();
   if(i==0){started.countDown();release.await(20,TimeUnit.SECONDS);}else socket.getOutputStream().write("next".getBytes("UTF-8"));
  }}catch(Exception error){if(!server.isClosed())throw new RuntimeException(error);}}});response.setDaemon(true);response.start();
  HostDownloads queue=new HostDownloads(new File(root,"queue"),root,new HostDownloads.Listener(){public void finished(HostDownloads.Record r){completed.incrementAndGet();}},source);
  try{
   long id=queue.enqueue("http://127.0.0.1:"+server.getLocalPort()+"/map",target,"","",Collections.<String,String>emptyMap(),"",2,new DownloadNetworkPolicy(2,true,true));
   check(started.await(5,TimeUnit.SECONDS),"request not received");Thread.sleep(100);observed.set(disconnected?null:NetworkCapabilities.shirazHost(NetworkCapabilities.TRANSPORT_WIFI));
   long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
   while(queue.get(id).status!=HostDownloads.PAUSED&&System.nanoTime()<end)Thread.sleep(20);
   check(queue.get(id).status==HostDownloads.PAUSED&&queue.get(id).reason==(disconnected?2:3),"blocked transfer ignored network policy loss");
   check(!target.exists()&&completed.get()==0&&requests.get()==1,"paused transfer published or completed");
   release.countDown();observed.set(allowed);end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
   while(queue.get(id).status!=HostDownloads.SUCCESSFUL&&System.nanoTime()<end)Thread.sleep(20);
   check(queue.get(id).status==HostDownloads.SUCCESSFUL&&Arrays.equals(Files.readAllBytes(target.toPath()),"next".getBytes("UTF-8")),"policy recovery failed");
  }finally{release.countDown();queue.close();server.close();response.join(2000);}
  check(requests.get()==2&&completed.get()==1,"retry completion count");
 }
 public static void main(String[] args)throws Exception{
  if(java.security.Security.getProvider("wolfJSSE")!=null)HostTlsProvider.install();
  run(true);run(false);
  System.out.println("DOWNLOAD_BLOCKED_POLICY_OK");
 }
}
