// SPDX-License-Identifier: GPL-3.0-only
import android.animation.*;
import android.os.*;
import android.view.*;
public final class ViewPropertyAnimatorSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static void near(float value,float expected){check(Math.abs(value-expected)<.001f,"Value "+value+" expected "+expected);}
 private static long time;
 private static void frame(long advance){time+=advance;Looper.myLooper().shirazDispatchDue(100);Choreographer.getInstance().shirazDoFrame(time*1000000,null);}
 public static void main(String[] args){Looper.prepareMainLooper();try{
  time=SystemClock.uptimeMillis();View view=new View(new android.content.ContextWrapper(null));ViewTreeObserver observer=ViewTreeObserver.shirazCreateWindowObserver();view.shirazAttachTree(observer);
  ViewPropertyAnimator a=view.animate();check(a==view.animate(),"View caches animator");StringBuilder events=new StringBuilder();
  a.setDuration(100).setInterpolator(null).setListener(new AnimatorListenerAdapter(){public void onAnimationStart(Animator v){events.append('S');}public void onAnimationCancel(Animator v){events.append('C');}public void onAnimationEnd(Animator v){events.append('E');}});
  a.alpha(0).translationX(100).withStartAction(new Runnable(){public void run(){events.append('A');}}).withEndAction(new Runnable(){public void run(){events.append('B');}});
  check(events.length()==0&&!view.hasTransientState(),"batch starts next animation frame");frame(0);check(events.toString().equals("AS")&&view.hasTransientState(),"batched start order");frame(16);view.clearAnimation();frame(50);near(view.getAlpha(),.5f);near(view.getTranslationX(),50);frame(50);near(view.getAlpha(),0);near(view.getTranslationX(),100);check(events.toString().equals("ASEB")&&!view.hasTransientState(),"natural completion and action");
  events.setLength(0);a.setStartDelay(100).alpha(1).withEndAction(new Runnable(){public void run(){throw new AssertionError("Canceled end action");}}).start();check(events.length()==0&&view.hasTransientState(),"delayed start");a.cancel();check(events.toString().equals("SCE")&&!view.hasTransientState(),"delayed cancel listeners, no end action");near(view.getAlpha(),0);
  a.setStartDelay(0);events.setLength(0);a.translationX(200).translationY(100).start();frame(16);frame(50);near(view.getTranslationX(),150);near(view.getTranslationY(),50);
  a.translationX(300).start();frame(16);frame(34);near(view.getTranslationY(),100);check(view.hasTransientState(),"replacement keeps other property running");frame(66);near(view.getTranslationX(),300);check(!view.hasTransientState(),"overlapping batches release transient state");
  final Animator[] underlying={null};a.setListener(new AnimatorListenerAdapter(){public void onAnimationStart(Animator value){underlying[0]=value;}});a.translationYBy(40).start();underlying[0].end();near(view.getTranslationY(),140);check(!view.hasTransientState(),"underlying end applies final values");
  a.alpha(1).start();frame(16);frame(25);float canceled=view.getAlpha();a.cancel();frame(100);near(view.getAlpha(),canceled);
  a.setDuration(100).setInterpolator(null).setStartDelay(0).scaleX(2).scaleY(.5f).start();frame(16);frame(100);near(view.getScaleX(),2);near(view.getScaleY(),.5f);a.scaleXBy(-1).scaleYBy(.5f).start();frame(16);frame(100);near(view.getScaleX(),1);near(view.getScaleY(),1);
  view.shirazDetachTree();a.translationX(500);frame(100);near(view.getTranslationX(),300);view.shirazAttachTree(observer);frame(16);frame(16);frame(100);near(view.getTranslationX(),500);
  a.translationX(700).start();frame(16);view.shirazDetachTree();frame(50);near(view.getTranslationX(),600);frame(50);near(view.getTranslationX(),700);check(!view.hasTransientState(),"running UI animation completes after detach");
  view.setHasTransientState(true);a.translationY(0).start();underlying[0].end();check(view.hasTransientState(),"animation release preserves external transient owner");view.setHasTransientState(false);
  a.translationY(500);a.cancel();view.shirazAttachTree(observer);frame(16);frame(200);near(view.getTranslationY(),0);check(!view.hasTransientState(),"pending cancel survives attach");

  a.setStartDelay(10).withStartAction(new Runnable(){public void run(){throw new IllegalStateException("delayed start action");}}).alpha(1).start();
  try{frame(100);throw new AssertionError("Delayed start action failure swallowed");}catch(IllegalStateException expected){check(expected.getMessage().equals("delayed start action"),"original start action failure");}
  check(!view.hasTransientState(),"delayed start action failure releases state");
  a.setListener(new AnimatorListenerAdapter(){public void onAnimationStart(Animator value){throw new IllegalStateException("delayed listener");}}).alpha(1).start();
  try{frame(100);throw new AssertionError("Delayed listener failure swallowed");}catch(IllegalStateException expected){check(expected.getMessage().equals("delayed listener"),"original listener failure");}
  check(!view.hasTransientState(),"delayed listener failure releases state");
  a.setStartDelay(0).setListener(new AnimatorListenerAdapter(){public void onAnimationCancel(Animator value){throw new IllegalStateException("cancel listener");}}).alpha(1).start();
  try{a.cancel();throw new AssertionError("Cancel failure swallowed");}catch(IllegalStateException expected){}
  check(!view.hasTransientState(),"cancel listener failure releases state");
  a.setListener(null).translationX(10).start();frame(16);frame(100);near(view.getTranslationX(),10);check(!view.hasTransientState(),"animation recovers after callbacks throw");
  a.setDuration(100).setInterpolator(null).setStartDelay(0).scaleX(2).scaleY(.5f).start();frame(16);frame(100);near(view.getScaleX(),2);near(view.getScaleY(),.5f);a.scaleXBy(-1).scaleYBy(.5f).start();frame(16);frame(100);near(view.getScaleX(),1);near(view.getScaleY(),1);
  view.shirazDetachTree();observer.shirazKill();System.out.println("VIEW_PROPERTY_ANIMATOR_TIMING_LIFECYCLE_OK");
 }finally{Looper.shirazShutdownMain();}}
}
