// SPDX-License-Identifier: GPL-3.0-only
import android.os.FileObserver;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
public final class FileObserverSmoke {
 static final class Notice {final int event;final String path;Notice(int e,String p){event=e;path=p;}}
 static void expect(BlockingQueue<Notice> queue,int event,String path)throws Exception{
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
  while(System.nanoTime()<deadline){Notice n=queue.poll(100,TimeUnit.MILLISECONDS);if(n!=null&&(n.event&event)!=0&&Objects.equals(path,n.path))return;}
  throw new AssertionError("Missing event "+event+" path="+path);
 }
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("shiraz-watch-");
  boolean closeEvents=true;
  FileObserver capability=new FileObserver(dir.toFile(),FileObserver.OPEN){public void onEvent(int event,String path){}};
  try{capability.startWatching();}catch(UnsupportedOperationException expected){closeEvents=false;}finally{capability.stopWatching();}
  if(!closeEvents){
   for(int unsupported:new int[]{FileObserver.ACCESS,FileObserver.CLOSE_WRITE,FileObserver.CLOSE_NOWRITE}){
    FileObserver rejected=new FileObserver(dir.toFile(),unsupported){public void onEvent(int event,String path){throw new AssertionError("Fabricated event");}};
    try{rejected.startWatching();throw new AssertionError("Unsupported event accepted");}catch(UnsupportedOperationException expected){}finally{rejected.stopWatching();}
   }
  }
  BlockingQueue<Notice> events=new LinkedBlockingQueue<Notice>();
  FileObserver observer=new FileObserver(dir.toFile(),FileObserver.CREATE|(closeEvents?FileObserver.CLOSE_WRITE:0)|FileObserver.MOVED_FROM|FileObserver.MOVED_TO|FileObserver.DELETE){public void onEvent(int event,String path){events.add(new Notice(event,path));}};
  try{
   observer.startWatching();observer.startWatching();
   Path first=dir.resolve("first"),second=dir.resolve("second");
   Files.write(first,new byte[]{1,2,3});expect(events,FileObserver.CREATE,"first");if(closeEvents)expect(events,FileObserver.CLOSE_WRITE,"first");
   Files.move(first,second);expect(events,FileObserver.MOVED_FROM,"first");expect(events,FileObserver.MOVED_TO,"second");
   BlockingQueue<Notice> fileEvents=new LinkedBlockingQueue<Notice>();
   FileObserver file=new FileObserver(second.toString(),FileObserver.MODIFY){public void onEvent(int event,String path){fileEvents.add(new Notice(event,path));stopWatching();}};
   file.startWatching();try{Files.write(second,new byte[]{4});expect(fileEvents,FileObserver.MODIFY,null);}finally{file.stopWatching();}
   Files.delete(second);expect(events,FileObserver.DELETE,"second");
   observer.stopWatching();observer.stopWatching();events.clear();
   Files.write(first,new byte[]{5});Thread.sleep(150);if(!events.isEmpty())throw new AssertionError("Events after stop");
   observer.startWatching();Files.delete(first);expect(events,FileObserver.DELETE,"first");
   // Multiple native read batches and long UTF-8 names must retain record boundaries.
   Set<String> pending=new HashSet<String>();
   for(int i=0;i<128;i++){
    String name="burst-"+i+"-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-é";
    pending.add(name);Files.write(dir.resolve(name),new byte[]{1});
   }
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
   while(!pending.isEmpty()&&System.nanoTime()<deadline){
    Notice notice=events.poll(100,TimeUnit.MILLISECONDS);
    if(notice!=null&&(notice.event&FileObserver.CREATE)!=0)pending.remove(notice.path);
   }
   if(!pending.isEmpty())throw new AssertionError("Lost burst records: "+pending.size());
   System.out.println("FILE_OBSERVER_EVENTS_OK");
  }finally{observer.stopWatching();for(File child:dir.toFile().listFiles())Files.delete(child.toPath());Files.delete(dir);}
 }
}
