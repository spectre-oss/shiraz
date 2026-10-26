// SPDX-License-Identifier: GPL-3.0-only
public final class FrameAnimationSmoke {
 private static void backgroundFrames(){
  final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);final long[] times=new long[3];
  android.os.HandlerThread thread=new android.os.HandlerThread("frame-test"){@Override protected void onLooperPrepared(){final android.view.Choreographer scheduler=android.view.Choreographer.getInstance();scheduler.postFrameCallback(new android.view.Choreographer.FrameCallback(){int count;public void doFrame(long time){times[count++]=time;if(count==3)done.countDown();else scheduler.postFrameCallback(this);}});}};
  thread.start();try{if(!done.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Background frame callbacks stalled");if(times[0]<=0||times[1]<=times[0]||times[2]<=times[1])throw new AssertionError("Frame timestamps");}catch(InterruptedException e){throw new AssertionError(e);}finally{thread.quit();try{thread.join(1000);}catch(InterruptedException e){throw new AssertionError(e);}}
  System.out.println("BACKGROUND_CHOREOGRAPHER_OK");
 }
 private static void earlierFrame(){
  final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
  android.os.HandlerThread thread=new android.os.HandlerThread("earlier-frame"){@Override protected void onLooperPrepared(){android.view.Choreographer.getInstance().postFrameCallbackDelayed(new android.view.Choreographer.FrameCallback(){public void doFrame(long t){}},60000);}};
  thread.start();new android.os.Handler(thread.getLooper()).post(new Runnable(){public void run(){android.view.Choreographer.getInstance().postFrameCallback(new android.view.Choreographer.FrameCallback(){public void doFrame(long t){done.countDown();}});}});
  try{if(!done.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Earlier frame trapped behind delayed callback");}catch(InterruptedException e){throw new AssertionError(e);}finally{thread.quit();try{thread.join(1000);}catch(InterruptedException e){throw new AssertionError(e);}}
 }
 public static void main(String[] args){backgroundFrames();earlierFrame();android.content.res.Resources r=android.content.res.Resources.shirazOpen(args[0],96,160);try{org.shiraz.fixture.FrameAnimationFixture.check(r);}finally{r.shirazClose();}}
}
