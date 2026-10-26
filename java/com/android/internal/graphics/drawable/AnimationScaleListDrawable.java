// SPDX-License-Identifier: GPL-3.0-only
package com.android.internal.graphics.drawable;
import android.animation.ValueAnimator;
import android.graphics.drawable.*;
/** Selects the last static or animatable child according to the host animation scale. */
public class AnimationScaleListDrawable extends DrawableContainer implements Animatable {
 private int still=-1,animated=-1,selected=-2;
 private boolean requested;
 public void shirazAddDrawable(Drawable child){if(child==null)throw new NullPointerException("drawable");int index=addChild(child);if(child instanceof Animatable)animated=index;else still=index;refresh();}
 private boolean refresh(){int next=ValueAnimator.getDurationScale()==0?still:animated;if(next==selected)return false;Drawable old=getCurrent();if(old instanceof Animatable)((Animatable)old).stop();selected=next;boolean changed=selectDrawable(next);if(requested&&isVisible()&&getCurrent() instanceof Animatable)((Animatable)getCurrent()).start();return changed;}
 public boolean isStateful(){return true;}
 protected boolean onStateChange(int[] state){boolean changed=super.onStateChange(state);return refresh()||changed;}
 public void start(){requested=true;boolean changed=refresh();if(!changed&&isVisible()&&getCurrent() instanceof Animatable)((Animatable)getCurrent()).start();}
 public void stop(){requested=false;if(getCurrent() instanceof Animatable)((Animatable)getCurrent()).stop();}
 public boolean isRunning(){return getCurrent() instanceof Animatable&&((Animatable)getCurrent()).isRunning();}
 public boolean setVisible(boolean visible,boolean restart){boolean changed=super.setVisible(visible,restart);if(!visible){if(getCurrent() instanceof Animatable)((Animatable)getCurrent()).stop();}else if(requested&&(changed||restart))start();return changed;}
}
