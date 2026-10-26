// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** App-owned persistent HTTP download queue. Files are published only after a complete response. */
public final class HostDownloads implements AutoCloseable {
 public static final int PENDING=1,RUNNING=2,SUCCESSFUL=8,FAILED=16;
 public static final int ERROR_UNKNOWN=1000,ERROR_FILE=1001,ERROR_HTTP=1004,ERROR_TOO_MANY_REDIRECTS=1005,ERROR_EXISTS=1009;
 private static final long MAX_BYTES=8L*1024*1024*1024;
 public interface Listener {void finished(Record record);}
 public static final class Record {
  public final long id;public final String url,title,mime,description;public final File destination;
  public final int status,reason,visibility;public final long bytes,total,modified;
  private Record(long id,Properties p){this.id=id;url=p.getProperty("url");title=p.getProperty("title","");mime=p.getProperty("mime","");description=p.getProperty("description","");visibility=Integer.parseInt(p.getProperty("visibility","0"));destination=new File(p.getProperty("destination"));status=Integer.parseInt(p.getProperty("status"));reason=Integer.parseInt(p.getProperty("reason","0"));bytes=Long.parseLong(p.getProperty("bytes","0"));total=Long.parseLong(p.getProperty("total","-1"));modified=Long.parseLong(p.getProperty("modified","0"));}
 }
 private final File directory,allowedRoot;private final Listener listener;private final Thread worker;
 private volatile boolean closed;private volatile HttpURLConnection active;
 public HostDownloads(File directory,File allowedRoot,Listener listener)throws IOException{
  this.directory=directory.getCanonicalFile();this.allowedRoot=allowedRoot.getCanonicalFile();this.listener=listener;
  Files.createDirectories(this.directory.toPath(),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
  Files.setPosixFilePermissions(this.directory.toPath(),PosixFilePermissions.fromString("rwx------"));
  worker=new Thread(new Runnable(){public void run(){work();}},"Shiraz downloads");worker.setDaemon(true);worker.start();
 }
 private File metadata(long id){if(id<=0)throw new IllegalArgumentException("Download id");return new File(directory,id+".properties");}
 private File cancelled(long id){return new File(directory,id+".cancel");}
 private File destination(File value)throws IOException{
  File result=value.getCanonicalFile();
  if(!result.toPath().startsWith(allowedRoot.toPath())||result.equals(allowedRoot))throw new SecurityException("Download destination is outside app storage");
  return result;
 }
 private static URL address(String value)throws IOException{
  URL url=new URL(value);if(!"http".equals(url.getProtocol())&&!"https".equals(url.getProtocol()))throw new IllegalArgumentException("Download requires HTTP(S)");
  if(url.getUserInfo()!=null)throw new IllegalArgumentException("Use request headers for download credentials");return url;
 }
 private Properties load(long id)throws IOException{
  File file=metadata(id);if(!file.isFile())return null;
  if(file.length()>128*1024)throw new IOException("Download metadata limit");
  Properties p=new Properties();try(InputStream in=new FileInputStream(file)){p.load(in);}return p;
 }
 private void save(long id,Properties p)throws IOException{
  p.setProperty("modified",Long.toString(System.currentTimeMillis()));
  ByteArrayOutputStream encoded=new ByteArrayOutputStream();p.store(encoded,"Shiraz download");if(encoded.size()>128*1024)throw new IOException("Download metadata limit");
  Path temp=Files.createTempFile(directory.toPath(),"record-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
  try{try(FileOutputStream out=new FileOutputStream(temp.toFile())){encoded.writeTo(out);out.getFD().sync();}Files.move(temp,metadata(id).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
 }
 private synchronized void audit(long id,String event)throws IOException{
  try(FileWriter out=new FileWriter(new File(directory,"access-history.log"),true)){out.write(System.currentTimeMillis()+" download "+id+" "+event+"\n");}
 }
 public long enqueue(String url,File destination,String title,String mime,Map<String,String> headers)throws IOException{
  return enqueue(url,destination,title,mime,headers,"",0);
 }
 public long enqueue(String url,File destination,String title,String mime,Map<String,String> headers,String description,int visibility)throws IOException{
  if(description==null||description.length()>4096||visibility<0||visibility>3)throw new IllegalArgumentException("Download presentation");
  address(url);File target=destination(destination);
  if(url.length()>16384||title!=null&&title.length()>4096||mime!=null&&mime.length()>1024||headers.size()>64)throw new IllegalArgumentException("Download request limit");
  Properties p=new Properties();p.setProperty("description",description);p.setProperty("visibility",Integer.toString(visibility));p.setProperty("url",url);p.setProperty("destination",target.getPath());p.setProperty("title",title==null?"":title);p.setProperty("mime",mime==null?"":mime);p.setProperty("status",Integer.toString(PENDING));
  int headerBytes=0;int index=0;for(Map.Entry<String,String> entry:headers.entrySet()){
   String key=entry.getKey(),value=entry.getValue();if(key==null||value==null||!key.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")||value.indexOf('\r')>=0||value.indexOf('\n')>=0||key.length()>256||value.length()>4096)throw new IllegalArgumentException("Download header");
   headerBytes+=key.length()+value.length();if(headerBytes>60000)throw new IllegalArgumentException("Download header limit");
   p.setProperty("header."+index+".name",key);p.setProperty("header."+index+".value",value);index++;
  }p.setProperty("headers",Integer.toString(index));
  synchronized(this){if(closed)throw new IllegalStateException("Download queue closed");}
  // Exclusive creation supplies IDs without a process-local counter or collisions across restarts.
  for(int retry=0;retry<100;retry++){
   long id=(UUID.randomUUID().getMostSignificantBits()&Long.MAX_VALUE);if(id==0)continue;
   File reservation=new File(directory,id+".reserved");if(!reservation.createNewFile())continue;
   try{if(metadata(id).exists())continue;save(id,p);audit(id,"queued");synchronized(this){notifyAll();}return id;}finally{reservation.delete();}
  }throw new IOException("Cannot allocate download id");
 }
 public Record get(long id)throws IOException{Properties p=load(id);return p==null||cancelled(id).exists()?null:new Record(id,p);}
 public List<Record> query()throws IOException{
  String[] files=directory.list();if(files==null)throw new IOException("Cannot list download queue");
  ArrayList<Record> result=new ArrayList<Record>();for(String file:files)if(file.matches("[0-9]+\\.properties")){Record record=get(Long.parseLong(file.substring(0,file.indexOf('.'))));if(record!=null)result.add(record);}
  Collections.sort(result,new Comparator<Record>(){public int compare(Record a,Record b){return Long.compare(b.modified,a.modified);}});return result;
 }
 public boolean remove(long id)throws IOException{
  Record record=get(id);if(record==null)return false;
  if(!cancelled(id).createNewFile())return false;audit(id,"removed");
  // The worker owns publication and cleanup; a cancellation marker remains until it acknowledges removal.
  synchronized(this){notifyAll();}return true;
 }
 private void check(long id)throws IOException{if(closed||cancelled(id).exists())throw new InterruptedIOException("Download cancelled or queue closed");}
 private void work(){
  while(!closed){
   try(RandomAccessFile file=new RandomAccessFile(new File(directory,"worker.lock"),"rw");FileChannel channel=file.getChannel()){
    FileLock lock=null;try{lock=channel.tryLock();}catch(OverlappingFileLockException busy){}
    if(lock!=null)try{
     String[] files=directory.list();if(files==null)throw new IOException("Download queue unavailable");
     for(String name:files){if(closed)break;if(!name.matches("[0-9]+\\.properties"))continue;long id=Long.parseLong(name.substring(0,name.indexOf('.')));Properties p=load(id);if(p==null)continue;
      if(cancelled(id).exists()){cleanup(id,p);continue;}
      int status=Integer.parseInt(p.getProperty("status"));if(status==PENDING||status==RUNNING)download(id,p);
     }
    }finally{lock.release();}
   }catch(IOException|RuntimeException error){System.err.println("SHIRAZ_DOWNLOAD_QUEUE_ERROR "+error.getClass().getSimpleName());}
   synchronized(this){if(!closed)try{wait(250);}catch(InterruptedException ignored){}}
  }
 }
 private Path partial(Properties p,File target)throws IOException{
  String path=p.getProperty("partial");if(path==null)return null;
  File file=destination(new File(path));if(!file.getParentFile().equals(target.getParentFile())||!file.getName().startsWith(".shiraz-download-")||!file.getName().endsWith(".part"))throw new IOException("Invalid partial download");return file.toPath();
 }
 private void cleanup(long id,Properties p)throws IOException{
  File target=destination(new File(p.getProperty("destination")));
  // Only successful records own their final file; never remove a pre-existing destination after a failed request.
  if(Integer.parseInt(p.getProperty("status"))==SUCCESSFUL)Files.deleteIfExists(target.toPath());
  Path part=partial(p,target);if(part!=null){if(Files.exists(part)&&target.exists()&&Files.isSameFile(part,target.toPath()))Files.delete(target.toPath());Files.deleteIfExists(part);}
  Files.deleteIfExists(metadata(id).toPath());Files.deleteIfExists(cancelled(id).toPath());
 }
 private void download(long id,Properties p)throws IOException{
  Path part=null;HttpURLConnection connection=null;int reason=ERROR_UNKNOWN;
  try{
   check(id);File target=destination(new File(p.getProperty("destination")));
   Path previous=partial(p,target);
   if(previous!=null&&Files.exists(previous)){
    if(target.exists()&&Files.isSameFile(previous,target.toPath())){
     p.setProperty("status",Integer.toString(SUCCESSFUL));p.setProperty("reason","0");save(id,p);Files.delete(previous);audit(id,"recovered-completion");if(listener!=null)try{listener.finished(new Record(id,p));}catch(RuntimeException error){System.err.println("SHIRAZ_DOWNLOAD_COMPLETION_ERROR "+error.getClass().getSimpleName());}return;
    }
    Files.delete(previous);
   }
   if(target.exists()){reason=ERROR_EXISTS;throw new IOException("Destination exists");}
   Files.createDirectories(target.getParentFile().toPath());target=destination(target);
   part=Files.createTempFile(target.getParentFile().toPath(),".shiraz-download-",".part",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
   p.setProperty("partial",part.toString());
   p.setProperty("status",Integer.toString(RUNNING));p.setProperty("bytes","0");save(id,p);audit(id,"started");
   URL initial=address(p.getProperty("url")),current=initial;
   for(int redirects=0;;redirects++){
    check(id);connection=(HttpURLConnection)current.openConnection();active=connection;connection.setConnectTimeout(15000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
    connection.setRequestProperty("Accept-Encoding","identity");
    if(initial.getProtocol().equals(current.getProtocol())&&initial.getAuthority().equals(current.getAuthority()))for(int i=0;i<Integer.parseInt(p.getProperty("headers","0"));i++)connection.setRequestProperty(p.getProperty("header."+i+".name"),p.getProperty("header."+i+".value"));
    int status=connection.getResponseCode();
    if(status==301||status==302||status==303||status==307||status==308){String location=connection.getHeaderField("Location");connection.disconnect();if(redirects>=5||location==null){reason=ERROR_TOO_MANY_REDIRECTS;throw new IOException("Redirect limit");}URL next=address(new URL(current,location).toString());if("https".equals(current.getProtocol())&&!"https".equals(next.getProtocol()))throw new IOException("Insecure redirect");current=next;continue;}
    if(status!=200){reason=status>=400&&status<=599?status:ERROR_HTTP;throw new IOException("HTTP response");}break;
   }
   long total=connection.getContentLengthLong();if(total>MAX_BYTES){reason=ERROR_HTTP;throw new IOException("Download size limit");}p.setProperty("total",Long.toString(total));
   if(p.getProperty("mime","").isEmpty()&&connection.getContentType()!=null)p.setProperty("mime",connection.getContentType());
   long count=0,last=0;byte[] buffer=new byte[65536];
   try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(part.toFile())){
    for(int n;(n=in.read(buffer))!=-1;){check(id);count+=n;if(count>MAX_BYTES){reason=ERROR_HTTP;throw new IOException("Download size limit");}out.write(buffer,0,n);p.setProperty("bytes",Long.toString(count));long now=System.currentTimeMillis();if(now-last>=500){save(id,p);last=now;}}
    out.getFD().sync();
   }
   if(total>=0&&count!=total){reason=ERROR_HTTP;throw new IOException("Truncated response");}check(id);
   // Hard-link publication is atomic, stays on the destination filesystem, and cannot overwrite another file.
   Files.createLink(target.toPath(),part);
   p.setProperty("status",Integer.toString(SUCCESSFUL));p.setProperty("reason","0");save(id,p);Files.delete(part);part=null;audit(id,"completed");
  }catch(IOException|RuntimeException error){
   if(cancelled(id).exists()){cleanup(id,p);return;}
   if(closed){p.setProperty("status",Integer.toString(PENDING));p.setProperty("bytes","0");save(id,p);return;}
   if(error instanceof FileAlreadyExistsException)reason=ERROR_EXISTS;
   p.setProperty("status",Integer.toString(FAILED));p.setProperty("reason",Integer.toString(reason));save(id,p);audit(id,"failed");
  }finally{active=null;if(connection!=null)connection.disconnect();if(part!=null)Files.deleteIfExists(part);}
  if(cancelled(id).exists()){cleanup(id,p);return;}
  if(listener!=null)try{listener.finished(new Record(id,p));}catch(RuntimeException error){System.err.println("SHIRAZ_DOWNLOAD_COMPLETION_ERROR "+error.getClass().getSimpleName());}
 }
 public void close(){closed=true;HttpURLConnection connection=active;if(connection!=null)connection.disconnect();synchronized(this){notifyAll();}if(Thread.currentThread()!=worker)try{worker.join(20000);}catch(InterruptedException error){Thread.currentThread().interrupt();}}
}
