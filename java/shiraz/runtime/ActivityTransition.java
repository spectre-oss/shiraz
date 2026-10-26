// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Context;
import android.graphics.*;
import android.view.animation.*;
/** Bounded GPU/software composition of activity surfaces; does not retain stopped View trees. */
public final class ActivityTransition implements AutoCloseable {
 private Bitmap outgoing,incoming;
 private Animation enter,exit;
 private long started=-1;
 private boolean closing,extendEdges;
 private float scale;
 public boolean isRunning(){return outgoing!=null;}
 public boolean hasCachedContent(){return outgoing!=null&&started>=0;}
 public void start(Bitmap frame,Context context,int[] custom,boolean back,boolean disabled){
  close();scale=android.animation.ValueAnimator.getDurationScale();if("1".equals(System.getenv("SHIRAZ_TRACE_FRAMES")))System.err.println("SHIRAZ_NAVIGATION back="+back+" disabled="+disabled+" scale="+scale+" custom="+(custom==null?"default":java.util.Arrays.toString(custom)));if(disabled||scale==0)return;
  Animation nextEnter,nextExit;
  if(custom!=null){nextEnter=load(context,custom[0]);nextExit=load(context,custom[1]);}
  else{nextEnter=defaultAnimation(back,true);nextExit=defaultAnimation(back,false);}
  if(nextEnter==null&&nextExit==null)return;
  outgoing=frame.copy(Bitmap.Config.ARGB_8888,false);
  try{incoming=Bitmap.createBitmap(frame.getWidth(),frame.getHeight(),Bitmap.Config.ARGB_8888);}catch(RuntimeException|Error e){close();throw e;}
  enter=nextEnter;exit=nextExit;closing=back;extendEdges=custom==null;
  for(Animation a:new Animation[]{enter,exit})if(a!=null){a.restrictDuration(10000);a.initialize(frame.getWidth(),frame.getHeight(),frame.getWidth(),frame.getHeight());a.setStartTime(0);}
 }
 // Android 14 activity_open/close: horizontal 10% travel, 450ms emphasized easing.
 // Independently implemented from AOSP resource timing/geometry, not XML copies.
 private static Animation defaultAnimation(boolean back,boolean entering){
  Path path=new Path();path.moveTo(0,0);path.cubicTo(.05f,0,.133333f,.06f,.166666f,.4f);path.cubicTo(.208333f,.82f,.25f,1,1,1);
  AnimationSet set=new AnimationSet(false);
  float from=entering?(back?-.1f:.1f):0,to=entering?0:(back?.1f:-.1f);
  TranslateAnimation move=new TranslateAnimation(Animation.RELATIVE_TO_PARENT,from,Animation.RELATIVE_TO_PARENT,to,Animation.RELATIVE_TO_PARENT,0,Animation.RELATIVE_TO_PARENT,0);
  move.setDuration(450);move.setInterpolator(new PathInterpolator(path));move.setFillEnabled(true);move.setFillBefore(true);move.setFillAfter(true);set.addAnimation(move);
  AlphaAnimation alpha=new AlphaAnimation(!back&&entering?0:1,back&&!entering?0:1);alpha.setDuration(83);alpha.setStartOffset(back&&!entering?35:!back&&entering?50:0);alpha.setInterpolator(new LinearInterpolator());alpha.setFillEnabled(true);alpha.setFillBefore(true);alpha.setFillAfter(true);set.addAnimation(alpha);return set;
 }
 private static Animation load(Context context,int id){return id==0?null:AnimationUtils.loadAnimation(context,id);}
 /** Render fresh app content here only when invalidated; animation never dirties it. */
 public Bitmap drawingSurface(Bitmap frame){return outgoing==null?frame:incoming;}
 public boolean compose(Bitmap frame,long now){
  if(outgoing!=null&&frame.getWidth()==outgoing.getWidth()&&frame.getHeight()==outgoing.getHeight())frame.shirazCopyPixelsInto(incoming);
  return composeCached(frame,now);
 }
 public boolean composeCached(Bitmap frame,long now){
  if(outgoing==null)return false;
  if(frame.getWidth()!=outgoing.getWidth()||frame.getHeight()!=outgoing.getHeight()){close();return false;}
  if(!android.animation.ValueAnimator.areAnimatorsEnabled()){incoming.shirazCopyPixelsInto(frame);close();return false;}
  if(started<0)started=now;
  long time=(long)(Math.max(0,now-started)/(double)scale);
  if(time>=10000){incoming.shirazCopyPixelsInto(frame);close();return false;}
  Transformation in=new Transformation(),out=new Transformation();
  boolean moreIn=enter!=null&&enter.getTransformation(time,in),moreOut=exit!=null&&exit.getTransformation(time,out);
  if((!moreIn&&!moreOut)||((enter==null||enter.hasEnded())&&(exit==null||exit.hasEnded()))){incoming.shirazCopyPixelsInto(frame);close();return false;}
  frame.eraseColor(0xff000000);Canvas canvas=Canvas.shirazScreen(frame);
  if(closing){draw(canvas,incoming,in);draw(canvas,outgoing,out);}else{draw(canvas,outgoing,out);draw(canvas,incoming,in);}
  return true;
 }
 private void draw(Canvas canvas,Bitmap bitmap,Transformation transform){if(transform.getAlpha()<=0)return;int save=canvas.save();try{canvas.concat(transform.getMatrix());if(transform.hasClipRect())canvas.clipRect(transform.getClipRect());Paint paint=new Paint();paint.setAlpha(Math.max(0,Math.min(255,Math.round(transform.getAlpha()*255))));if(extendEdges){int w=bitmap.getWidth(),h=bitmap.getHeight(),edge=(int)Math.ceil(w*.1);canvas.drawBitmap(bitmap,new Rect(0,0,1,h),new Rect(-edge,0,0,h),paint);canvas.drawBitmap(bitmap,new Rect(w-1,0,w,h),new Rect(w,0,w+edge,h),paint);}canvas.drawBitmap(bitmap,0,0,paint);}finally{canvas.restoreToCount(save);}}
 public void close(){if(outgoing!=null)outgoing.recycle();if(incoming!=null)incoming.recycle();outgoing=incoming=null;enter=exit=null;started=-1;}
}
