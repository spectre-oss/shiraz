// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.animation.*;
import java.util.ArrayList;
/** Software animated vectors driven by the host animation frame scheduler. */
public class AnimatedVectorDrawable extends DrawableWrapper implements Animatable2 {
 private final AnimatorSet animations=new AnimatorSet();
 private final ArrayList<Animator> targets=new ArrayList<Animator>();
 private final ArrayList<Animatable2.AnimationCallback> callbacks=new ArrayList<Animatable2.AnimationCallback>();
 public AnimatedVectorDrawable(){this(null);}
 public AnimatedVectorDrawable(VectorDrawable vector){super(vector);animations.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator animation){for(Animatable2.AnimationCallback cb:new ArrayList<Animatable2.AnimationCallback>(callbacks))cb.onAnimationEnd(AnimatedVectorDrawable.this);}});}
 public void shirazAddTarget(String name,Animator animator){if(animations.isStarted())throw new IllegalStateException("Animated vector running");if(targets.size()>=256)throw new IllegalArgumentException("Animated vector target limit");if(!(getDrawable() instanceof VectorDrawable))throw new IllegalArgumentException("Vector drawable required");if(!(animator instanceof ObjectAnimator)&&!(animator instanceof AnimatorSet))throw new UnsupportedOperationException("Animated vector needs property animations");animator.setTarget(((VectorDrawable)getDrawable()).shirazTarget(name));targets.add(animator);animations.playTogether(targets);}
 public boolean canApplyTheme(){return false;}
 public void applyTheme(android.content.res.Resources.Theme theme){throw new UnsupportedOperationException("Animated vector theme changes require resource reinflation");}
 public void start(){if(animations.isStarted()||targets.isEmpty())return;for(Animatable2.AnimationCallback cb:new ArrayList<Animatable2.AnimationCallback>(callbacks))cb.onAnimationStart(this);animations.start();invalidateSelf();}
 public void stop(){if(animations.isStarted()){animations.end();}invalidateSelf();}
 public boolean isRunning(){return animations.isRunning();}
 public void registerAnimationCallback(Animatable2.AnimationCallback callback){if(callback!=null&&!callbacks.contains(callback))callbacks.add(callback);}
 public boolean unregisterAnimationCallback(Animatable2.AnimationCallback callback){return callback!=null&&callbacks.remove(callback);}
 public void clearAnimationCallbacks(){callbacks.clear();}
 public boolean setVisible(boolean visible,boolean restart){boolean changed=super.setVisible(visible,restart);if(!visible)stop();return changed;}
 public void jumpToCurrentState(){stop();super.jumpToCurrentState();}
}
