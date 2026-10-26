// SPDX-License-Identifier: GPL-3.0-only
package android.transition;
import android.animation.Animator;
import android.view.View;
import android.view.ViewGroup;
/** Transition metadata stub; animated scene changes are not played. */
public abstract class Transition implements Cloneable {
 public interface TransitionListener {
  void onTransitionStart(Transition transition);
  void onTransitionEnd(Transition transition);
  void onTransitionCancel(Transition transition);
  void onTransitionPause(Transition transition);
  void onTransitionResume(Transition transition);
 }
 public Transition addTarget(View target){return this;}
 public Transition addTarget(int targetId){return this;}
 public Transition removeTarget(View target){return this;}
 public Transition addListener(TransitionListener listener){return this;}
 public Transition removeListener(TransitionListener listener){return this;}
 public Transition setDuration(long duration){return this;}
 public long getDuration(){return -1;}
 public Transition setStartDelay(long startDelay){return this;}
 public long getStartDelay(){return -1;}
 public Animator createAnimator(ViewGroup sceneRoot,TransitionValues startValues,TransitionValues endValues){return null;}
 public void captureStartValues(TransitionValues transitionValues){}
 public void captureEndValues(TransitionValues transitionValues){}
 @Override public Transition clone(){try{return (Transition)super.clone();}catch(CloneNotSupportedException error){throw new AssertionError(error);}}
}
