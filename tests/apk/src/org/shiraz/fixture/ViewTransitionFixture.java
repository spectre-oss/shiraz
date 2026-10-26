// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;
public final class ViewTransitionFixture {
 public static class Child extends View {public int attaches,detaches;public boolean failDetach;public Child(Context c){super(c);setBackgroundColor(Color.RED);setClickable(true);}protected void onAttachedToWindow(){attaches++;}protected void onDetachedFromWindow(){detaches++;if(failDetach)throw new IllegalStateException("fixture detach");}}
 public static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static FrameLayout root(Context c,Child child){FrameLayout root=new FrameLayout(c);root.addView(child,new FrameLayout.LayoutParams(20,20));root.measure(View.MeasureSpec.makeMeasureSpec(40,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(30,View.MeasureSpec.EXACTLY));root.layout(0,0,40,30);return root;}
 public static void removed(FrameLayout root,Child child){root.startViewTransition(child);root.startViewTransition(child);root.removeView(child);check(root.getChildCount()==0&&root.indexOfChild(child)==-1&&child.getParent()==root,"Removed transition child excluded from active hierarchy");child.setAlpha(.5f);Bitmap bitmap=Bitmap.createBitmap(40,30,Bitmap.Config.ARGB_8888);try{root.draw(new Canvas(bitmap));check(bitmap.getPixel(5,5)==0x80ff0000,"Disappearing child keeps drawing with opacity");}finally{bitmap.recycle();}MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,5,5,0);try{check(!root.dispatchTouchEvent(down),"Removed child excluded from input");}finally{down.recycle();}}
 public static void names(Context c){View first=new View(c),second=new View(c);check(first.getTransitionName()==null,"Default transition name");first.setTransitionName("route:حافظ");check("route:حافظ".equals(first.getTransitionName())&&second.getTransitionName()==null,"Independent exact transition names");first.setTransitionName("");check("".equals(first.getTransitionName()),"Empty transition name retained");first.setTransitionName(null);check(first.getTransitionName()==null,"Transition name clearing");}
 public static void check(Context c){names(c);Child child=new Child(c);FrameLayout root=root(c,child);removed(root,child);root.endViewTransition(child);root.endViewTransition(child);check(child.getParent()==null,"Transition end releases parent");root.addView(child);root.startViewTransition(child);root.endViewTransition(child);check(root.getChildCount()==1&&child.getParent()==root,"Ending active child transition preserves hierarchy");System.out.println("VIEW_TRANSITION_RETENTION_OK");}
}
