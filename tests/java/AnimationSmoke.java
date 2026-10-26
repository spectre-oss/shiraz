// SPDX-License-Identifier: GPL-3.0-only
import android.os.Looper;
import android.view.*;
public final class AnimationSmoke {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static void frame(Choreographer c){c.shirazDoFrame(System.nanoTime(),null);}
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();final Choreographer c=Choreographer.getInstance();
  try{
   try{c.getFrameTimeNanos();throw new AssertionError("Frame time outside callback");}catch(IllegalStateException expected){}
   final StringBuilder order=new StringBuilder();final Object token=new Object();
   c.postCallback(3,new Runnable(){public void run(){order.append('C');}},null);
   c.postCallback(1,new Runnable(){public void run(){order.append('A');c.postCallback(1,new Runnable(){public void run(){order.append('N');}},null);c.postCallback(2,new Runnable(){public void run(){order.append('T');}},null);}},null);
   c.postCallback(0,new Runnable(){public void run(){order.append('I');}},null);
   c.shirazDoFrame(123456789L,new Runnable(){public void run(){check(c.getFrameTimeNanos()==123456789L,"shared frame time");order.append('V');}});
   check(order.toString().equals("IATVC"),"phase order");frame(c);check(order.toString().equals("IATVCN"),"same phase deferred");
   Runnable canceled=new Runnable(){public void run(){throw new AssertionError("canceled callback");}};c.postCallback(1,canceled,token);c.removeCallbacks(1,null,token);frame(c);
   final int[] count={0};Choreographer.FrameCallback callback=new Choreographer.FrameCallback(){public void doFrame(long time){check(time==c.getFrameTimeNanos(),"FrameCallback timestamp");count[0]++;}};
   c.postFrameCallback(callback);c.removeFrameCallback(callback);frame(c);check(count[0]==0,"frame removal");
   Thread worker=new Thread(new Runnable(){public void run(){c.postFrameCallback(callback);}});worker.start();worker.join();frame(c);check(count[0]==1,"cross-thread post");
   c.postFrameCallbackDelayed(callback,Long.MAX_VALUE);frame(c);check(count[0]==1,"overflowing delay ran early");c.removeFrameCallback(callback);
   c.postFrameCallbackDelayed(callback,20);android.os.SystemClock.sleep(25);frame(c);check(count[0]==2,"delayed frame callback");
   c.postCallback(1,new Runnable(){public void run(){throw new IllegalArgumentException("intentional");}},null);
   try{frame(c);throw new AssertionError("callback failure swallowed");}catch(IllegalArgumentException expected){check(expected.getMessage().equals("intentional"),"callback identity");}
   c.postFrameCallback(callback);frame(c);check(count[0]==3,"exception poisoned dispatcher");
   View view=new View(new android.content.ContextWrapper(null));final int[] actions={0};Runnable action=new Runnable(){public void run(){actions[0]++;}};
   view.postOnAnimation(action);view.post(action);view.postOnAnimation(canceled);view.removeCallbacks(canceled);Looper.myLooper().shirazDispatchDue(100);frame(c);check(actions[0]==0,"unattached action ran");
   view.shirazAttachTree(ViewTreeObserver.shirazCreateWindowObserver());Looper.myLooper().shirazDispatchDue(100);check(actions[0]==2,"pending action transfer");view.postOnAnimation(action);Looper.myLooper().shirazDispatchDue(100);check(actions[0]==2,"attached animation ran as handler");frame(c);check(actions[0]==3,"attached animation delivery");
   view.postOnAnimation(canceled);view.removeCallbacks(canceled);frame(c);view.shirazDetachTree();view.postOnAnimation(action);frame(c);check(actions[0]==3,"detached action delivered");view.shirazAttachTree(ViewTreeObserver.shirazCreateWindowObserver());Looper.myLooper().shirazDispatchDue(100);check(actions[0]==4,"reattachment transfer");view.shirazDetachTree();
   View invalidating=new View(new android.content.ContextWrapper(null));invalidating.shirazAttachTree(ViewTreeObserver.shirazCreateWindowObserver());android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888);invalidating.draw(new android.graphics.Canvas(bitmap));
   invalidating.postInvalidateOnAnimation();invalidating.postInvalidateOnAnimation(0,0,1,1);check(!invalidating.shirazIsInvalidated(),"frame invalidation ran inline");frame(c);check(invalidating.shirazIsInvalidated(),"frame invalidation missing");invalidating.draw(new android.graphics.Canvas(bitmap));
   invalidating.postInvalidateOnAnimation();invalidating.shirazDetachTree();frame(c);check(!invalidating.shirazIsInvalidated(),"detached invalidation survived");bitmap.recycle();
   for(int i=0;i<4096;i++)c.postFrameCallbackDelayed(callback,Long.MAX_VALUE);try{c.postFrameCallback(callback);throw new AssertionError("callback queue unbounded");}catch(IllegalStateException expected){}c.removeFrameCallback(callback);
   c.postFrameCallbackDelayed(callback,Long.MAX_VALUE);Looper.shirazShutdownMain();
   try{c.postFrameCallback(callback);throw new AssertionError("closed scheduler accepted work");}catch(IllegalStateException expected){}
   Looper.prepareMainLooper();check(Choreographer.getInstance()!=c,"scheduler survived main teardown");
   System.out.println("FRAME_SCHEDULER_LIFECYCLE_OK");
  }finally{Looper.shirazShutdownMain();}
 }
}
