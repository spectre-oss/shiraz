// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.view.*;
import android.widget.*;
public final class FrameMeasurePassSmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static final class Leaf extends View {
  int calls,w,h;
  Leaf(Context context,int w,int h){super(context);this.w=w;this.h=h;}
  protected void onMeasure(int ws,int hs){calls++;setMeasuredDimension(resolveSize(w,ws),resolveSize(h,hs));}
 }
 static int atMost(int n){return View.MeasureSpec.makeMeasureSpec(n,View.MeasureSpec.AT_MOST);}
 public static void main(String[] args){
  android.os.Looper.prepareMainLooper();Context context=new ContextWrapper(null);
  Leaf leaf=new Leaf(context,100,60);View child=leaf;
  for(int i=0;i<6;i++){FrameLayout frame=new FrameLayout(context);frame.addView(child,new FrameLayout.LayoutParams(-1,-1));child=frame;}
  child.measure(atMost(300),atMost(200));
  check(leaf.calls==1,"nested single-child frames repeated measurement: "+leaf.calls);
  check(child.getMeasuredWidth()==100&&child.getMeasuredHeight()==60,"natural size");
  child.layout(0,0,100,60);leaf.w=140;leaf.h=80;leaf.requestLayout();child.measure(atMost(300),atMost(200));
  check(child.getMeasuredWidth()==140&&child.getMeasuredHeight()==80&&leaf.calls==2,"requested size update lost");
  child.layout(0,0,140,80);child.measure(atMost(90),atMost(70));
  check(child.getMeasuredWidth()==90&&child.getMeasuredHeight()==70,"viewport shrink lost");
  FrameLayout siblings=new FrameLayout(context);Leaf first=new Leaf(context,40,20),second=new Leaf(context,80,50);
  siblings.addView(first,new FrameLayout.LayoutParams(-1,-1));siblings.addView(second,new FrameLayout.LayoutParams(-1,-1));siblings.measure(atMost(300),atMost(200));
  check(siblings.getMeasuredWidth()==80&&siblings.getMeasuredHeight()==50,"sibling parent size");
  check(first.getMeasuredWidth()==80&&first.getMeasuredHeight()==50&&second.getMeasuredWidth()==80,"multiple matching children must fill resolved parent");
  FrameLayout hidden=new FrameLayout(context);Leaf shown=new Leaf(context,40,20),gone=new Leaf(context,80,50);gone.setVisibility(View.GONE);
  hidden.addView(shown,new FrameLayout.LayoutParams(-1,-1));hidden.addView(gone,new FrameLayout.LayoutParams(-1,-1));hidden.measure(atMost(300),atMost(200));
  check(shown.calls==1&&gone.calls==0&&hidden.getMeasuredWidth()==40,"gone child counted for second pass");
  hidden.setMeasureAllChildren(true);hidden.measure(atMost(300),atMost(200));
  check(hidden.getMeasuredWidth()==80&&shown.getMeasuredWidth()==80&&gone.calls>0,"measureAllChildren lost");
  System.out.println("FRAME_MEASURE_PASS_OK");
 }
}
