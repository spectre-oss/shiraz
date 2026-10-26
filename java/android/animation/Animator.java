// SPDX-License-Identifier: GPL-3.0-only
package android.animation;
import java.util.ArrayList;
public abstract class Animator implements Cloneable {
 public static final long DURATION_INFINITE=-1;
 public interface AnimatorListener {
  void onAnimationStart(Animator animation);void onAnimationEnd(Animator animation);void onAnimationCancel(Animator animation);void onAnimationRepeat(Animator animation);
  default void onAnimationStart(Animator animation,boolean reverse){onAnimationStart(animation);}default void onAnimationEnd(Animator animation,boolean reverse){onAnimationEnd(animation);}
 }
 protected ArrayList<AnimatorListener> listeners=new ArrayList<AnimatorListener>();
 public void addListener(AnimatorListener listener){if(listener==null)throw new NullPointerException("listener");if(listeners.size()>=1024)throw new IllegalStateException("Animator listener limit");listeners.add(listener);}
 public void removeListener(AnimatorListener listener){listeners.remove(listener);}public void removeAllListeners(){listeners.clear();}public ArrayList<AnimatorListener> getListeners(){return listeners.isEmpty()?null:new ArrayList<AnimatorListener>(listeners);}
 public abstract void start();public abstract void cancel();public abstract void end();public abstract boolean isRunning();public boolean isStarted(){return isRunning();}
 public abstract Animator setDuration(long duration);public abstract long getDuration();public abstract void setStartDelay(long delay);public abstract long getStartDelay();public abstract void setInterpolator(TimeInterpolator interpolator);public TimeInterpolator getInterpolator(){return null;}
 public long getTotalDuration(){long d=getDuration(),s=getStartDelay();return d<0?DURATION_INFINITE:d>Long.MAX_VALUE-s?Long.MAX_VALUE:s+d;}
 public void setTarget(Object target){throw new UnsupportedOperationException("Animator has no target binding");}
 public void pause(){throw new UnsupportedOperationException("Animator pause is not implemented");}public void resume(){throw new UnsupportedOperationException("Animator resume is not implemented");}public boolean isPaused(){return false;}
 public Animator clone(){try{Animator clone=(Animator)super.clone();clone.listeners=new ArrayList<AnimatorListener>(listeners);return clone;}catch(CloneNotSupportedException error){throw new AssertionError(error);}}
}
