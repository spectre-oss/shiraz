// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.animation.Animator;
import android.animation.ValueAnimator;
/** Circular reveal implemented by the host frame scheduler and software view clipping. */
public final class ViewAnimationUtils {
 private ViewAnimationUtils(){}
 public static Animator createCircularReveal(View view,int centerX,int centerY,float startRadius,float endRadius){
  if(view==null)throw new NullPointerException("view");
  if(!Float.isFinite(startRadius)||!Float.isFinite(endRadius)||startRadius<0||endRadius<0)throw new IllegalArgumentException("Reveal radii must be finite and nonnegative");
  return new Reveal(view,centerX,centerY,startRadius,endRadius);
 }
 private static final class Reveal extends ValueAnimator {
  private final View view;private final int x,y;private boolean consumed,finished;
  Reveal(View view,int x,int y,float start,float end){this.view=view;this.x=x;this.y=y;setFloatValues(start,end);}
  @Override public void start(){if(consumed)throw new IllegalStateException("Reveal animations are one-shot");consumed=true;super.start();}
  @Override public void end(){if(finished)return;consumed=true;super.end();}
  @Override protected void shirazApplyValue(Object value){view.shirazSetRevealClip(this,x,y,((Number)value).floatValue());}
  @Override protected void shirazAnimationCleared(){finished=true;view.shirazClearRevealClip(this);}
  @Override public ValueAnimator clone(){throw new UnsupportedOperationException("Reveal animations cannot be cloned");}
 }
}
