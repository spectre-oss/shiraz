// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
import android.os.*;
public final class FrameMetricsSmoke {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();
  Choreographer choreographer=Choreographer.getInstance();choreographer.shirazDoFrame(123456789L,null);
  java.lang.reflect.Field lastFrame=Choreographer.class.getDeclaredField("mLastFrameTimeNanos");lastFrame.setAccessible(true);
  check(lastFrame.getLong(choreographer)==123456789L,"Observer timestamp is actual host frame time");
  Window window=new Window(new android.content.ContextWrapper(null));
  final int[] calls={0},drops={0};final FrameMetrics[] last={null};
  Window.OnFrameMetricsAvailableListener listener=new Window.OnFrameMetricsAvailableListener(){
   public void onFrameMetricsAvailable(Window w,FrameMetrics m,int dropped){
    check(w==window,"Window identity");calls[0]++;drops[0]=dropped;last[0]=new FrameMetrics(m);
   }
  };
  window.addOnFrameMetricsAvailableListener(listener,new Handler());
  try{window.addOnFrameMetricsAvailableListener(listener,null);throw new AssertionError("Duplicate");}catch(IllegalArgumentException expected){}
  window.shirazReportFrame(11,23);check(calls[0]==0,"Must dispatch asynchronously");
  Looper.myLooper().shirazDispatchDue(10);check(calls[0]==1&&last[0].getMetric(FrameMetrics.FIRST_DRAW_FRAME)==1,"First frame");
  check(last[0].getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)==11&&last[0].getMetric(FrameMetrics.DRAW_DURATION)==23,"Measured timings");
  check(last[0].getMetric(FrameMetrics.TOTAL_DURATION)==-1&&last[0].getMetric(FrameMetrics.VSYNC_TIMESTAMP)==-1&&last[0].getMetric(-1)==-1&&last[0].getMetric(900)==-1,"Unavailable timings");
  FrameMetrics retained=last[0];for(int i=0;i<10000;i++)window.shirazReportFrame(i,i+1);
  Looper.myLooper().shirazDispatchDue(10);check(calls[0]==2&&drops[0]==9999&&last[0].getMetric(FrameMetrics.DRAW_DURATION)==10000,"Bounded queue and drop count");
  check(retained.getMetric(FrameMetrics.DRAW_DURATION)==23&&last[0].getMetric(FrameMetrics.FIRST_DRAW_FRAME)==0,"Independent snapshots");
  window.shirazReportFrame(1,2);window.removeOnFrameMetricsAvailableListener(listener);Looper.myLooper().shirazDispatchDue(10);
  check(calls[0]==2&&!window.shirazObservesFrames(),"Removal cancels queued delivery");
  window.addOnFrameMetricsAvailableListener(listener,null);window.shirazReportFrame(3,4);window.shirazDestroy();Looper.myLooper().shirazDispatchDue(10);
  check(calls[0]==2&&!window.shirazObservesFrames(),"Destroy cancels delivery");
  try{window.addOnFrameMetricsAvailableListener(listener,null);throw new AssertionError("Destroyed window");}catch(IllegalStateException expected){}
  System.out.println("FRAME_METRICS_OK");
 }
}
