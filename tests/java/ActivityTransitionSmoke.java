// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
import shiraz.runtime.ActivityTransition;
public final class ActivityTransitionSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}public android.content.res.Resources.Theme getTheme(){return getResources().newTheme();}};
  Bitmap frame=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);ActivityTransition motion=new ActivityTransition();
  try{
   frame.eraseColor(Color.RED);motion.start(frame,context,null,false,false);check(motion.isRunning(),"Default opening transition loaded");
   frame.eraseColor(Color.BLUE);check(motion.compose(frame,1000),"Opening first frame");check((frame.getPixel(50,50)&0xff)==0,"Incoming page starts transparent");
   frame.eraseColor(Color.BLUE);check(motion.compose(frame,1100),"Opening intermediate frame");int middle=frame.getPixel(50,50);check((middle&255)>0&&((middle>>>16)&255)>0,"Pages blend during enter");
   frame.eraseColor(Color.BLUE);check(!motion.compose(frame,1500)&&frame.getPixel(50,50)==Color.BLUE&&!motion.isRunning(),"Opening completes without altering incoming page");
   frame.eraseColor(Color.RED);motion.start(frame,context,null,true,false);frame.eraseColor(Color.BLUE);motion.compose(frame,2000);check(frame.getPixel(50,50)==Color.RED,"Closing page stays above revealed page");frame.eraseColor(Color.BLUE);check(!motion.compose(frame,2500)&&frame.getPixel(50,50)==Color.BLUE,"Back completes");
   frame.eraseColor(Color.RED);motion.start(frame,context,null,false,false);motion.drawingSurface(frame).eraseColor(Color.BLUE);
   check(motion.composeCached(frame,3000),"Cached first frame");check(motion.composeCached(frame,3200),"Cached content survives composed output");
   check(!motion.composeCached(frame,3500)&&frame.getPixel(50,50)==Color.BLUE,"Cached final surface restored without redraw");
   motion.start(frame,context,null,false,false);motion.drawingSurface(frame).eraseColor(Color.GREEN);motion.composeCached(frame,4000);android.animation.ValueAnimator.setDurationScale(0);check(!motion.composeCached(frame,4010)&&frame.getPixel(50,50)==Color.GREEN,"Disabling motion restores fresh content");android.animation.ValueAnimator.setDurationScale(1);
   int fade=context.getResources().getIdentifier("fade_in","anim","android");check(fade!=0,"Framework custom animation resource");motion.start(frame,context,new int[]{fade,0},false,false);frame.eraseColor(Color.BLUE);check(motion.compose(frame,2400),"Custom app animation is played");motion.close();
   motion.start(frame,context,new int[]{0,0},false,false);check(!motion.isRunning(),"Explicit no-animation override");
   motion.start(frame,context,null,false,true);check(!motion.isRunning(),"No-animation intent");
   android.animation.ValueAnimator.setDurationScale(0);motion.start(frame,context,null,false,false);check(!motion.isRunning(),"Reduced motion");android.animation.ValueAnimator.setDurationScale(1);
   motion.start(frame,context,null,false,false);android.animation.ValueAnimator.setDurationScale(0);check(!motion.compose(frame,2900)&&!motion.isRunning(),"Reduced motion cancels active transition");android.animation.ValueAnimator.setDurationScale(1);
   motion.start(frame,context,null,false,false);Bitmap resized=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888);try{check(!motion.compose(resized,3000)&&!motion.isRunning(),"Resize cancels transition");}finally{resized.recycle();}
   android.app.Activity activity=new android.app.Activity();activity.overridePendingTransition(11,12);int[] custom=activity.shirazTakeTransition();check(custom[0]==11&&custom[1]==12&&activity.shirazTakeTransition()==null,"Transition override consumed once");
   System.out.println("ACTIVITY_TRANSITION_TIMING_CLEANUP_OK");
  }finally{motion.close();frame.recycle();android.animation.ValueAnimator.setDurationScale(1);}
 }
}
