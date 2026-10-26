// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;
import android.content.res.ColorStateList;
import android.content.res.Resources;

/** Delegating base for application-defined wrappers; subclasses own clone state. */
public abstract class DrawableWrapper extends Drawable implements Drawable.Callback {
 private Drawable child;
 public DrawableWrapper(Drawable drawable){setDrawable(drawable);}
 public Drawable getDrawable(){return child;}
 public void setDrawable(Drawable drawable){
  Drawable cursor=drawable;int depth=0;while(cursor!=null){if(cursor==this)throw new IllegalArgumentException("Drawable wrapper cycle");if(++depth>1024)throw new IllegalArgumentException("Drawable wrapper depth");cursor=cursor instanceof DrawableWrapper?((DrawableWrapper)cursor).getDrawable():null;}
  if(child!=null)child.setCallback(null);child=drawable;
  if(child!=null){child.setCallback(this);child.setVisible(isVisible(),true);child.setState(getState());child.setLevel(getLevel());child.setBounds(getBounds());child.setLayoutDirection(getLayoutDirection());}invalidateSelf();
 }
 public void invalidateDrawable(Drawable who){if(who==child)invalidateSelf();}
 public void scheduleDrawable(Drawable who,Runnable task,long when){if(who==child)scheduleSelf(task,when);}
 public void unscheduleDrawable(Drawable who,Runnable task){if(who==child)unscheduleSelf(task);}
 public void draw(Canvas canvas){if(child!=null)child.draw(canvas);}
 public void setAlpha(int alpha){if(child!=null)child.setAlpha(alpha);}public int getAlpha(){return child==null?255:child.getAlpha();}
 public void setColorFilter(ColorFilter filter){if(child!=null)child.setColorFilter(filter);}public ColorFilter getColorFilter(){return child==null?null:child.getColorFilter();}
 public boolean hasFocusStateSpecified(){return child!=null&&child.hasFocusStateSpecified();}
 public void setTintList(ColorStateList tint){if(child!=null)child.setTintList(tint);refreshTintOwner();}public void setTintMode(PorterDuff.Mode mode){if(child!=null)child.setTintMode(mode);}
 public int getOpacity(){return child==null?PixelFormat.TRANSPARENT:child.getOpacity();}
 public boolean isStateful(){return child!=null&&child.isStateful();}
 protected boolean onStateChange(int[] state){if(child==null||!child.isStateful())return false;boolean changed=child.setState(state);if(changed)onBoundsChange(getBounds());return changed;}
 protected boolean onLevelChange(int level){return child!=null&&child.setLevel(level);}
 protected void onBoundsChange(Rect bounds){if(child!=null)child.setBounds(bounds);}
 public boolean onLayoutDirectionChanged(int direction){return child!=null&&child.setLayoutDirection(direction);}
 public boolean setVisible(boolean visible,boolean restart){boolean changed=super.setVisible(visible,restart);return (child!=null&&child.setVisible(visible,restart))|changed;}
 public int getIntrinsicWidth(){return child==null?-1:child.getIntrinsicWidth();}public int getIntrinsicHeight(){return child==null?-1:child.getIntrinsicHeight();}
 public boolean getPadding(Rect out){return child==null?super.getPadding(out):child.getPadding(out);}
 public int getChangingConfigurations(){return super.getChangingConfigurations()|(child==null?0:child.getChangingConfigurations());}
 public void setHotspot(float x,float y){if(child!=null)child.setHotspot(x,y);}public void setHotspotBounds(int l,int t,int r,int b){if(child!=null)child.setHotspotBounds(l,t,r,b);}
 public void getHotspotBounds(Rect out){if(child==null)super.getHotspotBounds(out);else child.getHotspotBounds(out);}
 public void getOutline(Outline out){if(child==null)super.getOutline(out);else child.getOutline(out);}
 public boolean canApplyTheme(){return child!=null&&child.canApplyTheme();}public void applyTheme(Resources.Theme theme){if(child!=null&&child.canApplyTheme())child.applyTheme(theme);}
 public void jumpToCurrentState(){if(child!=null)child.jumpToCurrentState();}
 public Drawable mutate(){if(child!=null)child.mutate();return this;}
}
