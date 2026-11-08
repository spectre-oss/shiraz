// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

/** Frame container that displays one child and runs ordinary View animations. */
public class ViewAnimator extends FrameLayout {
 private int displayed;
 private boolean first=true,animateFirst=true;
 private Animation inAnimation,outAnimation;
 public ViewAnimator(Context context){super(context);setMeasureAllChildren(true);}
 public ViewAnimator(Context context,AttributeSet attrs){super(context,attrs);setMeasureAllChildren(true);}
 public void setDisplayedChild(int child){if(getChildCount()==0){displayed=0;return;}displayed=child>=getChildCount()?0:child<0?getChildCount()-1:child;showOnly(displayed,!first||animateFirst);first=false;}
 protected void showOnly(int child,boolean animate){for(int i=0;i<getChildCount();i++){View view=getChildAt(i);if(i==child){if(animate&&inAnimation!=null)view.startAnimation(inAnimation);view.setVisibility(VISIBLE);}else {if(view.getVisibility()==VISIBLE&&animate&&outAnimation!=null)view.startAnimation(outAnimation);else view.clearAnimation();view.setVisibility(GONE);}}}
 public int getDisplayedChild(){return displayed;}
 public View getCurrentView(){return getChildAt(displayed);}
 public void showNext(){setDisplayedChild(displayed+1);}
 public void showPrevious(){setDisplayedChild(displayed-1);}
 public void setInAnimation(Animation animation){inAnimation=animation;}
 public Animation getInAnimation(){return inAnimation;}
 public void setOutAnimation(Animation animation){outAnimation=animation;}
 public Animation getOutAnimation(){return outAnimation;}
 public void setInAnimation(Context context,int id){setInAnimation(AnimationUtils.loadAnimation(context,id));}
 public void setOutAnimation(Context context,int id){setOutAnimation(AnimationUtils.loadAnimation(context,id));}
 public void setAnimateFirstView(boolean animate){animateFirst=animate;}
 public boolean getAnimateFirstView(){return animateFirst;}
 @Override public void addView(View child,int index,ViewGroup.LayoutParams params){super.addView(child,index,params);child.setVisibility(getChildCount()==1?VISIBLE:GONE);if(index>=0&&displayed>=index&&getChildCount()>1)setDisplayedChild(displayed+1);}
 @Override public void removeViewAt(int index){super.removeViewAt(index);if(displayed>=getChildCount())displayed=getChildCount()-1;else if(index<displayed)displayed--;if(displayed<0){displayed=0;first=true;}showOnly(displayed,false);}
 @Override public void removeView(View view){int index=indexOfChild(view);if(index>=0)removeViewAt(index);}
 @Override public void removeViews(int start,int count){if(start<0||count<0||start>getChildCount()-count)throw new IndexOutOfBoundsException();for(int i=0;i<count;i++)removeViewAt(start);}
 @Override public void removeAllViews(){super.removeAllViews();displayed=0;first=true;}
 protected void resetFirstView(){first=true;}
}
