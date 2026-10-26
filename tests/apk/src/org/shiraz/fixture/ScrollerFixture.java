// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.widget.OverScroller;
import android.widget.Scroller;
import android.view.animation.LinearInterpolator;
public final class ScrollerFixture {
 public interface Clock {void at(long millis,Runnable action);}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void immediate(Context context){
  OverScroller over=new OverScroller(context);check(over.isFinished()&&!over.computeScrollOffset(),"Initial state");
  over.startScroll(10,20,30,-10,0);check(over.computeScrollOffset()&&over.isFinished()&&over.getCurrX()==40&&over.getCurrY()==10,"Zero-duration endpoint");
  check(!over.computeScrollOffset(),"Completed scroll stayed active");
  over.startScroll(0,0,50,70);over.forceFinished(true);check(over.getCurrX()==0&&over.getCurrY()==0,"Forced finish jumped");
  over.startScroll(0,0,50,70);over.abortAnimation();check(over.getCurrX()==50&&over.getCurrY()==70&&over.isFinished(),"Abort endpoint");
  Scroller legacy=new Scroller(context);legacy.startScroll(2,3,8,9,0);check(legacy.computeScrollOffset()&&legacy.getCurrX()==10&&legacy.getCurrY()==12,"Legacy endpoint");
  System.out.println("SCROLLER_ENDPOINTS_OK");
 }
 public static void timed(final Context context,Clock clock){
  final OverScroller over=new OverScroller(context,new LinearInterpolator());final Scroller legacy=new Scroller(context,new LinearInterpolator());
  clock.at(1000,new Runnable(){public void run(){over.startScroll(10,20,100,-40,200);legacy.startScroll(10,20,100,-40,200);}});
  clock.at(1100,new Runnable(){public void run(){check(over.computeScrollOffset()&&over.getCurrX()==60&&over.getCurrY()==0,"Linear midpoint");check(legacy.computeScrollOffset()&&legacy.getCurrX()==60&&legacy.getCurrY()==0,"Legacy midpoint");}});
  clock.at(1200,new Runnable(){public void run(){check(over.computeScrollOffset()&&over.isFinished()&&over.getCurrX()==110&&over.getCurrY()==-20,"Timed endpoint");}});
  clock.at(2000,new Runnable(){public void run(){over.fling(0,0,4000,0,0,200,0,0,30,0);check(!over.isFinished(),"Fling did not start");}});
  final boolean[] overshot={false};
  for(long t=2010;t<=5000;t+=10)clock.at(t,new Runnable(){public void run(){over.computeScrollOffset();check(over.getCurrX()>=0&&over.getCurrX()<=230&&over.getCurrY()==0,"Fling escaped over-range");overshot[0]|=over.getCurrX()>200;}});
  check(over.isFinished()&&over.getCurrX()==200&&overshot[0],"Fling failed to overshoot and settle");
  clock.at(6000,new Runnable(){public void run(){check(over.springBack(-30,250,0,100,0,200),"Spring failed to start");}});
  for(long t=6010;t<=8000;t+=10)clock.at(t,new Runnable(){public void run(){over.computeScrollOffset();check(over.getCurrX()>=-30&&over.getCurrX()<=0&&over.getCurrY()>=200&&over.getCurrY()<=250,"Spring escaped bounds");}});
  check(over.isFinished()&&over.getCurrX()==0&&over.getCurrY()==200,"Spring endpoint");
  final OverScroller normal=new OverScroller(context),sticky=new OverScroller(context);sticky.setFriction(.03f);
  clock.at(9000,new Runnable(){public void run(){normal.fling(0,0,2000,0,-100000,100000,0,0);sticky.fling(0,0,2000,0,-100000,100000,0,0);check(sticky.getFinalX()>0&&sticky.getFinalX()<normal.getFinalX(),"Friction did not shorten fling");}});
  System.out.println("SCROLLER_TIMED_FLING_SPRING_OK");
 }
}
