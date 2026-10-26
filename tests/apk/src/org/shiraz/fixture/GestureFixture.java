// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.os.SystemClock;
import android.view.*;
public final class GestureFixture {
 public interface Pump {void run();}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static final class Listener extends GestureDetector.SimpleOnGestureListener {
  int down,tap,confirmed,doubleTap,doubleEvents,scroll,fling,longPress,show;float vx,dx;
  public boolean onDown(MotionEvent e){down++;return true;}
  public boolean onSingleTapUp(MotionEvent e){tap++;return true;}
  public boolean onSingleTapConfirmed(MotionEvent e){confirmed++;return true;}
  public boolean onDoubleTap(MotionEvent e){doubleTap++;return true;}
  public boolean onDoubleTapEvent(MotionEvent e){doubleEvents++;return true;}
  public boolean onScroll(MotionEvent a,MotionEvent b,float x,float y){scroll++;dx=x;return true;}
  public boolean onFling(MotionEvent a,MotionEvent b,float x,float y){fling++;vx=x;return true;}
  public void onLongPress(MotionEvent e){longPress++;}
  public void onShowPress(MotionEvent e){show++;}
 }
 static void send(GestureDetector detector,long down,long time,int action,float x){MotionEvent e=MotionEvent.obtain(down,time,action,x,10,0);try{detector.onTouchEvent(e);}finally{e.recycle();}}
 static void sample(VelocityTracker tracker,long time,float x){MotionEvent e=MotionEvent.obtain(0,time,time==0?0:2,x,-x,0);try{tracker.addMovement(e);}finally{e.recycle();}}
 static void pair(VelocityTracker tracker,long time,int action){MotionEvent.PointerProperties[] properties={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};properties[0].id=3;properties[1].id=9;MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};coords[0].x=time;coords[1].x=-time;MotionEvent e=MotionEvent.obtain(0,time,action,2,properties,coords,0,0,1,1,0,0,0x1002,0);try{tracker.addMovement(e);}finally{e.recycle();}}
 static MotionEvent touch(long down,long time,int action,int[] ids,float[] xs,float[] ys,float[] pressures){MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[ids.length];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[ids.length];for(int i=0;i<ids.length;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=ids[i];props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=xs[i];coords[i].y=ys[i];coords[i].pressure=pressures[i];}return MotionEvent.obtain(down,time,action,ids.length,props,coords,0,0,1,1,0,0,0x1002,0);}
 public static void immediate(Context context){
  VelocityTracker tracker=VelocityTracker.obtain();sample(tracker,0,0);sample(tracker,10,10);sample(tracker,20,20);tracker.computeCurrentVelocity(1000);check(Math.abs(tracker.getXVelocity()-1000)<.1&&Math.abs(tracker.getYVelocity()+1000)<.1,"Linear velocity");tracker.computeCurrentVelocity(1000,200);check(tracker.getXVelocity()==200&&tracker.getYVelocity()==-200,"Velocity cap");
  tracker.clear();sample(tracker,0,0);sample(tracker,10,1);sample(tracker,20,4);tracker.computeCurrentVelocity(1000);check(Math.abs(tracker.getXVelocity()-400)<.1,"Quadratic endpoint velocity");sample(tracker,200,5);tracker.computeCurrentVelocity(1000);check(tracker.getXVelocity()==0,"Stale samples retained");tracker.clear();pair(tracker,0,2);pair(tracker,10,2);pair(tracker,20,2);tracker.computeCurrentVelocity(1000);check(Math.abs(tracker.getXVelocity(3)-1000)<.1&&Math.abs(tracker.getXVelocity(9)+1000)<.1,"Independent pointer velocities");pair(tracker,30,6);check(Math.abs(tracker.getXVelocity()+1000)<.1,"Active pointer handoff");tracker.clear();check(tracker.getXVelocity()==0,"Clear retained velocity");tracker.recycle();
  Listener listener=new Listener();GestureDetector detector=new GestureDetector(context,listener);long now=SystemClock.uptimeMillis();
  send(detector,now,now,0,10);send(detector,now,now+10,1,10);check(listener.down==1&&listener.tap==1,"Tap callbacks");
  send(detector,now+100,now+100,0,10);send(detector,now+100,now+110,1,10);check(listener.doubleTap==1&&listener.doubleEvents==2,"Double tap callbacks");send(detector,now,now+120,3,10);
  listener=new Listener();detector=new GestureDetector(context,listener);send(detector,now,now,0,0);send(detector,now,now+10,2,20);send(detector,now,now+20,2,40);send(detector,now,now+30,1,40);check(listener.scroll==2&&listener.dx==-20&&listener.fling==1&&Math.abs(listener.vx-2000)<1,"Scroll/fling callbacks");send(detector,now,now+40,3,40);
  final int[] scaling={0,0,0};final float[] factor={0};
  ScaleGestureDetector scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
   public boolean onScaleBegin(ScaleGestureDetector d){scaling[0]++;return true;}
   public boolean onScale(ScaleGestureDetector d){scaling[1]++;factor[0]=d.getScaleFactor();return true;}
   public void onScaleEnd(ScaleGestureDetector d){scaling[2]++;}
  });
  scale.onTouchEvent(touch(0,0,0,new int[]{3},new float[]{10},new float[]{20},new float[]{1}));
  scale.onTouchEvent(touch(0,1,261,new int[]{3,9},new float[]{10,30},new float[]{20,20},new float[]{1,1}));
  scale.onTouchEvent(touch(0,2,2,new int[]{3,9},new float[]{0,40},new float[]{20,20},new float[]{1,1}));
  check(scaling[0]==1&&scaling[1]==1&&Math.abs(factor[0]-2)<.001,"Pinch scale factor");
  scale.onTouchEvent(touch(0,3,6,new int[]{3,9},new float[]{0,40},new float[]{20,20},new float[]{0,1}));
  check(scaling[2]==1&&!scale.isInProgress()&&scale.getFocusX()==40,"Lifted pointer excluded; no spurious scale restart");
  System.out.println("GESTURE_TAP_SCROLL_VELOCITY_OK");
 }
 public static void delayed(Context context,Pump pump){
  Listener cancelled=new Listener();GestureDetector cancelDetector=new GestureDetector(context,cancelled);long cancelTime=SystemClock.uptimeMillis();send(cancelDetector,cancelTime-600,cancelTime-600,0,10);send(cancelDetector,cancelTime-600,cancelTime,3,10);pump.run();check(cancelled.longPress==0&&cancelled.show==0&&cancelled.confirmed==0,"Cancelled gesture callbacks fired");
  Listener listener=new Listener();GestureDetector detector=new GestureDetector(context,listener);long now=SystemClock.uptimeMillis();send(detector,now-600,now-600,0,10);pump.run();check(listener.show==1&&listener.longPress==1,"Show/long-press callbacks");send(detector,now-600,now,1,10);check(listener.tap==0&&listener.fling==0,"Long press generated tap/fling");send(detector,now,now,3,10);
  listener=new Listener();detector=new GestureDetector(context,listener);detector.setIsLongpressEnabled(false);check(!detector.isLongpressEnabled(),"Long press toggle");send(detector,now-600,now-600,0,10);pump.run();check(listener.longPress==0,"Disabled long press fired");send(detector,now-600,now,3,10);
  listener=new Listener();detector=new GestureDetector(context,listener);now=SystemClock.uptimeMillis();send(detector,now,now,0,10);send(detector,now,now+1,1,10);SystemClock.sleep(330);pump.run();check(listener.confirmed==1,"Single tap confirmation");send(detector,now,now+2,3,10);
  System.out.println("GESTURE_DELAYED_PRESS_CONFIRM_OK");
 }
}
