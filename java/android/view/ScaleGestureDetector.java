// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
import android.os.Handler;
public class ScaleGestureDetector {
 public interface OnScaleGestureListener {
  boolean onScale(ScaleGestureDetector detector);
  boolean onScaleBegin(ScaleGestureDetector detector);
  void onScaleEnd(ScaleGestureDetector detector);
 }
 public static class SimpleOnScaleGestureListener implements OnScaleGestureListener {
  public boolean onScale(ScaleGestureDetector detector){return false;}
  public boolean onScaleBegin(ScaleGestureDetector detector){return true;}
  public void onScaleEnd(ScaleGestureDetector detector){}
 }
 private final OnScaleGestureListener listener;
 private boolean inProgress,quickScaleEnabled=true,stylusScaleEnabled=true;
 private float focusX,focusY,currSpan,prevSpan,initialSpan,currSpanX,currSpanY,prevSpanX,prevSpanY;
 private long currTime,prevTime;
 public ScaleGestureDetector(Context context,OnScaleGestureListener listener){this(context,listener,null);}
 public ScaleGestureDetector(Context context,OnScaleGestureListener listener,Handler handler){
  if(listener==null)throw new NullPointerException("listener");
  this.listener=listener;
 }
 public boolean onTouchEvent(MotionEvent event){
  if(event==null)return false;
  currTime=event.getEventTime();
  int action=event.getActionMasked(),skip=action==MotionEvent.ACTION_POINTER_UP?event.getActionIndex():-1;
  int count=event.getPointerCount()-(skip>=0?1:0);
  if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL||count<2){
   if(inProgress){listener.onScaleEnd(this);inProgress=false;}
   if(count>0){int remaining=skip==0?1:0;focusX=event.getX(remaining);focusY=event.getY(remaining);}
   prevTime=currTime;return true;
  }
  float sx=0,sy=0;
  for(int i=0;i<event.getPointerCount();i++){if(i==skip)continue;sx+=event.getX(i);sy+=event.getY(i);}
  focusX=sx/count;focusY=sy/count;
  float dx=0,dy=0;
  for(int i=0;i<event.getPointerCount();i++){if(i==skip)continue;dx+=Math.abs(event.getX(i)-focusX);dy+=Math.abs(event.getY(i)-focusY);}
  float spanX=(dx/count)*2f,spanY=(dy/count)*2f,span=(float)Math.hypot(spanX,spanY);
  if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP||!inProgress){
   if(inProgress){listener.onScaleEnd(this);inProgress=false;}
   prevSpanX=currSpanX=spanX;prevSpanY=currSpanY=spanY;initialSpan=prevSpan=currSpan=span;prevTime=currTime;
   if(span>0)inProgress=listener.onScaleBegin(this);
   return true;
  }
  if(action==MotionEvent.ACTION_MOVE&&inProgress){
   currSpanX=spanX;currSpanY=spanY;currSpan=span;
   if(listener.onScale(this)){prevSpanX=currSpanX;prevSpanY=currSpanY;prevSpan=currSpan;prevTime=currTime;}
  }
  return true;
 }
 public void setQuickScaleEnabled(boolean scales){quickScaleEnabled=scales;}
 public boolean isQuickScaleEnabled(){return quickScaleEnabled;}
 public void setStylusScaleEnabled(boolean scales){stylusScaleEnabled=scales;}
 public boolean isStylusScaleEnabled(){return stylusScaleEnabled;}
 public boolean isInProgress(){return inProgress;}
 public float getFocusX(){return focusX;}
 public float getFocusY(){return focusY;}
 public float getCurrentSpan(){return currSpan;}
 public float getCurrentSpanX(){return currSpanX;}
 public float getCurrentSpanY(){return currSpanY;}
 public float getPreviousSpan(){return prevSpan;}
 public float getPreviousSpanX(){return prevSpanX;}
 public float getPreviousSpanY(){return prevSpanY;}
 public float getScaleFactor(){return prevSpan>0?currSpan/prevSpan:1f;}
 public long getTimeDelta(){return currTime-prevTime;}
 public long getEventTime(){return currTime;}
}
