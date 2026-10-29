// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Linux filesystem notifications, delivered on a shared daemon thread. */
public abstract class FileObserver {
 public static final int ACCESS=1, MODIFY=2, ATTRIB=4, CLOSE_WRITE=8, CLOSE_NOWRITE=16,
  OPEN=32, MOVED_FROM=64, MOVED_TO=128, CREATE=256, DELETE=512, DELETE_SELF=1024,
  MOVE_SELF=2048, ALL_EVENTS=4095;
 private static final List<Watch> active=new ArrayList<Watch>();
 private static Thread dispatcher;
 private final String[] paths;
 private final int mask;
 private Watch watch;
 static {System.loadLibrary("shiraz-host");}
 public FileObserver(String path){this(new File(path));}
 public FileObserver(String path,int mask){this(new File(path),mask);}
 public FileObserver(File file){this(file,ALL_EVENTS);}
 public FileObserver(File file,int mask){this(Collections.singletonList(file),mask);}
 public FileObserver(List<File> files){this(files,ALL_EVENTS);}
 public FileObserver(List<File> files,int mask){
  Objects.requireNonNull(files,"files");
  if(files.size()>1024)throw new IllegalArgumentException("Too many file watches");
  paths=new String[files.size()];
  for(int i=0;i<paths.length;i++)paths[i]=Objects.requireNonNull(files.get(i),"file").getAbsolutePath();
  this.mask=mask&ALL_EVENTS;
 }
 public synchronized void startWatching(){
  if(watch!=null)return;
  synchronized(active){
   if(active.size()>=1024)throw new IllegalStateException("Too many file observers");
   int fd=nativeOpen(paths,mask);
   if(fd<0)return;
   Watch next=new Watch(this,fd);
   active.add(next);watch=next;
   if(dispatcher==null){dispatcher=new Thread(new Runnable(){public void run(){dispatch();}},"FileObserver");dispatcher.setDaemon(true);dispatcher.start();}
   active.notifyAll();
  }
 }
 public synchronized void stopWatching(){
  Watch old=watch;watch=null;
  if(old!=null){old.close();synchronized(active){active.remove(old);}}
 }
 public abstract void onEvent(int event,String path);
 private static final class Watch {
  final WeakReference<FileObserver> owner;
  int fd;
  Watch(FileObserver observer,int descriptor){owner=new WeakReference<FileObserver>(observer);fd=descriptor;}
  synchronized void close(){if(fd>=0){nativeClose(fd);fd=-1;}}
  boolean poll(){
   FileObserver target=owner.get();
   byte[] bytes;
   synchronized(this){
    if(target==null||fd<0){close();return false;}
    bytes=nativeRead(fd);
   }
   if(bytes==null)return true;
   ByteBuffer data=ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
   while(data.remaining()>=16){
    synchronized(this){if(fd<0)return false;}
    data.getInt();int event=data.getInt();data.getInt();int length=data.getInt();
    if(length<0||length>data.remaining())throw new IllegalStateException("Invalid inotify record");
    int start=data.position(),end=start;
    while(end<start+length&&bytes[end]!=0)end++;
    String path=end==start?null:new String(bytes,start,end-start,StandardCharsets.UTF_8);
    data.position(start+length);
    // Kernel watch-removal/overflow records are not Android event types.
    if((event&ALL_EVENTS)!=0)try{target.onEvent(event,path);}catch(Throwable failure){android.util.Log.e("FileObserver","Observer callback failed",failure);}
   }
   synchronized(this){return fd>=0;}
  }
 }
 private static void dispatch(){
  for(;;){
   Watch[] snapshot;
   synchronized(active){
    while(active.isEmpty())try{active.wait();}catch(InterruptedException ignored){}
    snapshot=active.toArray(new Watch[active.size()]);
   }
   for(Watch item:snapshot){
    boolean keep;
    try{keep=item.poll();}catch(Throwable failure){item.close();keep=false;android.util.Log.e("FileObserver","Filesystem watch failed",failure);}
    if(!keep)synchronized(active){active.remove(item);}
   }
   try{Thread.sleep(50);}catch(InterruptedException ignored){}
  }
 }
 private static native int nativeOpen(String[] paths,int mask);
 private static native byte[] nativeRead(int fd);
 private static native void nativeClose(int fd);
}
