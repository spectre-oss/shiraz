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
  BlockingQueue<Notice> events=new LinkedBlockingQueue<Notice>();
  FileObserver observer=new FileObserver(dir.toFile(),FileObserver.CREATE|FileObserver.CLOSE_WRITE|FileObserver.MOVED_FROM|FileObserver.MOVED_TO|FileObserver.DELETE){public void onEvent(int event,String path){events.add(new Notice(event,path));}};
  try{
   observer.startWatching();observer.startWatching();
   Path first=dir.resolve("first"),second=dir.resolve("second");
   Files.write(first,new byte[]{1,2,3});expect(events,FileObserver.CREATE,"first");expect(events,FileObserver.CLOSE_WRITE,"first");
   Files.move(first,second);expect(events,FileObserver.MOVED_FROM,"first");expect(events,FileObserver.MOVED_TO,"second");
   BlockingQueue<Notice> fileEvents=new LinkedBlockingQueue<Notice>();
   FileObserver file=new FileObserver(second.toString(),FileObserver.MODIFY){public void onEvent(int event,String path){fileEvents.add(new Notice(event,path));stopWatching();}};
   file.startWatching();try{Files.write(second,new byte[]{4});expect(fileEvents,FileObserver.MODIFY,null);}finally{file.stopWatching();}
   Files.delete(second);expect(events,FileObserver.DELETE,"second");
   observer.stopWatching();observer.stopWatching();events.clear();
   Files.write(first,new byte[]{5});Thread.sleep(150);if(!events.isEmpty())throw new AssertionError("Events after stop");
   observer.startWatching();Files.delete(first);expect(events,FileObserver.DELETE,"first");
   System.out.println("FILE_OBSERVER_EVENTS_OK");
  }finally{observer.stopWatching();for(File child:dir.toFile().listFiles())Files.delete(child.toPath());Files.delete(dir);}
 }
}
