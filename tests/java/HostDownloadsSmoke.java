// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.HostDownloads;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
public final class HostDownloadsSmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static HostDownloads.Record done(HostDownloads queue,long id)throws Exception{
  long deadline=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<deadline){HostDownloads.Record r=queue.get(id);if(r!=null&&(r.status==8||r.status==16))return r;Thread.sleep(20);}throw new AssertionError("Download did not finish");
 }
 public static void main(String[] args)throws Exception{
  File root=Files.createTempDirectory("shiraz-download-test").toFile();ServerSocket server=new ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));
  Thread serve=new Thread(new Runnable(){public void run(){while(!server.isClosed())try{Socket socket=server.accept();new Thread(new Runnable(){public void run(){try(Socket owned=socket){BufferedReader in=new BufferedReader(new InputStreamReader(owned.getInputStream(),"UTF-8"));String request=in.readLine();boolean header=false;for(String line;(line=in.readLine())!=null&&!line.isEmpty();)if(line.equals("X-Test: yes"))header=true;
   if(request.contains("/slow ")){owned.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 1048576\r\n\r\n".getBytes("UTF-8"));byte[] chunk=new byte[1024];for(int i=0;i<1024;i++){owned.getOutputStream().write(chunk);owned.getOutputStream().flush();try{Thread.sleep(10);}catch(InterruptedException error){return;}}return;}
   String response;if(request.contains("/redirect "))response="HTTP/1.1 302 Found\r\nLocation: /data\r\nContent-Length: 0\r\n\r\n";
   else if(request.contains("/truncated "))response="HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nshort";
   else if(!header)response="HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n";
   else response="HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: 7\r\n\r\npayload";
   owned.getOutputStream().write(response.getBytes("UTF-8"));}catch(IOException ignored){}}}).start();}catch(IOException ignored){}}});serve.setDaemon(true);serve.start();
  String base="http://127.0.0.1:"+server.getLocalPort();Map<String,String> headers=Collections.singletonMap("X-Test","yes");File state=new File(root,"queue");long successful;
  try(HostDownloads queue=new HostDownloads(state,root,null)){
   successful=queue.enqueue(base+"/redirect",new File(root,"result.apk"),"test","",headers);HostDownloads.Record r=done(queue,successful);
   check(r.status==8&&r.bytes==7&&r.total==7,"Real HTTP/redirect/progress");check(new String(Files.readAllBytes(r.destination.toPath()),"UTF-8").equals("payload"),"Downloaded bytes");
   long failed=queue.enqueue(base+"/truncated",new File(root,"broken.apk"),"","",headers);check(done(queue,failed).status==16&&!new File(root,"broken.apk").exists(),"Truncated content not published");
   long denied=queue.enqueue(base+"/data",new File(root,"denied.apk"),"","",Collections.emptyMap());check(done(queue,denied).reason==403,"HTTP failure reason");
   long slow=queue.enqueue(base+"/slow",new File(root,"slow.apk"),"","",headers);
   long deadline=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<deadline&&queue.get(slow).bytes==0)Thread.sleep(20);
   check(queue.get(slow).bytes>0,"Active download progress");check(queue.remove(slow)&&queue.get(slow)==null,"Cancel active request");
   while(System.currentTimeMillis()<deadline&&new File(state,slow+".properties").exists())Thread.sleep(20);
   check(!new File(state,slow+".properties").exists()&&!new File(root,"slow.apk").exists(),"Cancellation acknowledges cleanup");
   long existing=queue.enqueue(base+"/data",r.destination,"","",headers);check(done(queue,existing).reason==1009,"Existing destination preserved");queue.remove(existing);Thread.sleep(300);check(r.destination.exists(),"Removing failed request must not delete existing file");
   try{queue.enqueue(base+"/data",new File(root,"../escape.apk"),"","",headers);throw new AssertionError("Destination escape");}catch(SecurityException expected){}
   try{queue.enqueue(base+"/data",new File(root,"invalid.apk"),"","",Collections.singletonMap("X-Test","a\r\nb"));throw new AssertionError("Header injection");}catch(IllegalArgumentException expected){}
  }
  // Recreate the durable publication boundary: hard link exists, success metadata has not yet been committed.
  Path partial=new File(root,".shiraz-download-recovery.part").toPath();Files.write(partial,"recovered".getBytes("UTF-8"));File recovered=new File(root,"recovered.apk");Files.createLink(recovered.toPath(),partial);
  Properties journal=new Properties();journal.setProperty("url",base+"/data");journal.setProperty("destination",recovered.getPath());journal.setProperty("partial",partial.toString());journal.setProperty("status","2");journal.setProperty("bytes","9");journal.setProperty("total","9");try(OutputStream out=new FileOutputStream(new File(state,"42.properties"))){journal.store(out,"");}
  try(HostDownloads reopened=new HostDownloads(state,root,null)){
   check(done(reopened,42).status==8&&!Files.exists(partial)&&recovered.exists(),"Interrupted publication recovery");
   check(reopened.get(successful).status==8,"Persistent successful record");check(reopened.remove(successful)&&reopened.get(successful)==null,"Removal hides record immediately");Thread.sleep(500);check(!new File(root,"result.apk").exists(),"Remove owned file");
  }finally{server.close();}
  check(new File(state,"access-history.log").isFile(),"Access audit");
  String audit=new String(Files.readAllBytes(new File(state,"access-history.log").toPath()),"UTF-8");check(!audit.contains(base)&&!audit.contains("X-Test")&&audit.contains("completed"),"Metadata-only audit");
  System.out.println("HOST_DOWNLOAD_HTTP_PERSISTENCE_FAILURES_OK");
 }
}
