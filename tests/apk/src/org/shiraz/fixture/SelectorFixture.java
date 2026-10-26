// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.*;
public final class SelectorFixture {
 private static final int ENABLED=16842910,CHECKED=16842912;
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static void pixel(Drawable d,int color){Bitmap b=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);try{d.setBounds(0,0,8,8);d.draw(new Canvas(b));check(b.getPixel(4,4)==color,"Selected pixels");}finally{b.recycle();}}
 private static class Sized extends ColorDrawable {public int getIntrinsicWidth(){return 9;}public int getIntrinsicHeight(){return 11;}public boolean getPadding(Rect out){out.set(1,2,3,4);return true;}}
 public static void check(Resources resources){
  int[] positive={ENABLED,-CHECKED};check(android.util.StateSet.stateSetMatches(positive,new int[]{ENABLED,0,CHECKED}),"State zero terminator");check(!android.util.StateSet.stateSetMatches(positive,new int[]{ENABLED,CHECKED}),"Negative state rejects");check(android.util.StateSet.stateSetMatches(new int[]{CHECKED},CHECKED)&&!android.util.StateSet.stateSetMatches(new int[]{-CHECKED},CHECKED),"Single state matching");check(android.util.StateSet.stateSetMatches(android.util.StateSet.WILD_CARD,(int[])null)&&android.util.StateSet.isWildCard(android.util.StateSet.NOTHING),"Wildcard/null semantics");int[] trimmed=android.util.StateSet.trimStateSet(new int[]{ENABLED,CHECKED,0},2);check(trimmed.length==2&&trimmed[1]==CHECKED&&android.util.StateSet.trimStateSet(trimmed,2)==trimmed,"Trim state array and identity");
  StateListDrawable d=new StateListDrawable();ColorDrawable checked=new ColorDrawable(Color.GREEN),normal=new ColorDrawable(Color.BLUE);d.addState(new int[]{CHECKED},checked);d.addState(new int[0],normal);
  check(d.isStateful()&&d instanceof DrawableContainer&&d.getCurrent()==normal,"Initial wildcard selection");pixel(d,Color.BLUE);d.setState(new int[]{ENABLED,CHECKED});check(d.getCurrent()==checked&&!normal.isVisible()&&checked.isVisible(),"Checked child visibility");pixel(d,Color.GREEN);check(!d.setState(new int[]{ENABLED,CHECKED}),"Repeated state unchanged");
  d.setAlpha(128);check(checked.getAlpha()==128,"Selected alpha");d.setState(new int[]{ENABLED});check(normal.getAlpha()==128,"Alpha follows selection");d.setAlpha(255);
  d.setVisible(false,false);check(!normal.isVisible(),"Parent hides active child");d.setState(new int[]{CHECKED});check(!checked.isVisible(),"Hidden state switch stays hidden");d.setVisible(true,false);
  final int[] events={0,0};d.setCallback(new Drawable.Callback(){public void invalidateDrawable(Drawable who){events[0]++;}public void scheduleDrawable(Drawable who,Runnable task,long when){events[1]++;}public void unscheduleDrawable(Drawable who,Runnable task){events[1]--;}});int before=events[0];normal.invalidateSelf();check(events[0]==before,"Inactive child does not invalidate parent");checked.invalidateSelf();check(events[0]==before+1,"Active child invalidates parent");Runnable task=new Runnable(){public void run(){}};normal.scheduleSelf(task,0);checked.scheduleSelf(task,0);check(events[1]==1,"Only active child schedules");checked.unscheduleSelf(task);check(events[1]==0,"Active callback cancellation");
  StateListDrawable sparse=new StateListDrawable();sparse.addState(new int[]{CHECKED},new Sized());check(sparse.getCurrent()==null&&sparse.getIntrinsicWidth()==-1,"No match clears active child");sparse.setState(new int[]{CHECKED});check(sparse.getIntrinsicWidth()==9&&sparse.getIntrinsicHeight()==11,"Selected dimensions");Rect padding=new Rect();check(sparse.getPadding(padding)&&padding.equals(new Rect(1,2,3,4)),"Constant padding across states");sparse.setState(new int[0]);check(sparse.getCurrent()==null,"Leaving match clears selection");
  int[] spec={CHECKED};StateListDrawable copy=new StateListDrawable();Drawable one=new ColorDrawable(Color.RED);copy.addState(spec,one);spec[0]=ENABLED;copy.setState(new int[]{CHECKED});check(copy.getCurrent()==one,"State spec copied");
  int id=resources.getIdentifier("state_selector","drawable","org.shiraz.fixture");Drawable xml=resources.getDrawable(id);check(xml instanceof StateListDrawable,"Compiled selector class");check(xml.getIntrinsicWidth()==-1&&xml.getIntrinsicHeight()==-1,"Selector ignores root size attributes like Android");pixel(xml,Color.RED);xml.setState(new int[]{ENABLED});pixel(xml,Color.BLUE);xml.setState(new int[]{ENABLED,CHECKED});pixel(xml,Color.GREEN);xml.setState(new int[]{CHECKED});pixel(xml,Color.RED);
  try{d.setEnterFadeDuration(10);throw new AssertionError("Unsupported fade accepted");}catch(UnsupportedOperationException expected){}
  System.out.println("SELECTOR_STATE_RENDER_LIFECYCLE_OK");
 }
}
