// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.LinearLayout;
public final class LinearLayoutFixture {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static int exact(int n){return View.MeasureSpec.makeMeasureSpec(n,View.MeasureSpec.EXACTLY);}
 private static int loose(int n){return View.MeasureSpec.makeMeasureSpec(n,View.MeasureSpec.AT_MOST);}
 private static void layout(LinearLayout root,int w,int h){root.measure(exact(w),exact(h));root.layout(0,0,w,h);}
 private static final class Leaf extends View {
  int color,desiredW,desiredH,clicks;float x,y;
  Leaf(Context context,int color,int w,int h){super(context);this.color=color;desiredW=w;desiredH=h;setOnClickListener(new OnClickListener(){public void onClick(View v){clicks++;}});}
  protected void onMeasure(int w,int h){setMeasuredDimension(resolveSizeAndState(desiredW,w,0),resolveSizeAndState(desiredH,h,0));}
  protected void onDraw(Canvas c){c.drawColor(color);}
  public boolean onTouchEvent(MotionEvent event){x=event.getX();y=event.getY();return super.onTouchEvent(event);}
 }
 public static void checkXml(Context context){
  int id=context.getResources().getIdentifier("linear_weights","layout",context.getPackageName());
  LinearLayout root=(LinearLayout)LayoutInflater.from(context).inflate(id,null,false);layout(root,10,42);
  check(root.getOrientation()==LinearLayout.VERTICAL&&root.getChildAt(0).getHeight()==10&&root.getChildAt(1).getTop()==11&&root.getChildAt(1).getHeight()==30,"compiled XML orientation and weights");
  Bitmap pixels=Bitmap.createBitmap(10,42,Bitmap.Config.ARGB_8888);root.draw(new Canvas(pixels));
  check(pixels.getPixel(0,0)==0&&pixels.getPixel(1,1)==Color.RED&&pixels.getPixel(8,40)==Color.BLUE&&pixels.getPixel(9,41)==0,"compiled linear XML pixels");pixels.recycle();
  System.out.println("SHIRAZ_FIXTURE_LINEAR_XML_OK");
 }
 private static final class VerticalOnly extends LinearLayout {
  VerticalOnly(Context context){super(context,null,0);setOrientation(VERTICAL);}
  @Override public void setOrientation(int value){if(value!=VERTICAL)throw new IllegalArgumentException("vertical-only subclass");super.setOrientation(value);}
  @Override public void setGravity(int value){throw new AssertionError("absent gravity must not invoke subclass setter");}
  @Override public void setBaselineAligned(boolean value){throw new AssertionError("default baseline must not invoke subclass setter");}
 }
 private static final class BaselineLeaf extends View {
  final int baseline;BaselineLeaf(Context context,int baseline){super(context);this.baseline=baseline;}
  @Override public int getBaseline(){return baseline;}
 }
 private static void baselines(Context context){
  LinearLayout root=new LinearLayout(context);BaselineLeaf first=new BaselineLeaf(context,8),second=new BaselineLeaf(context,3);root.addView(first,new LinearLayout.LayoutParams(10,12));root.addView(second,new LinearLayout.LayoutParams(10,20));
  root.measure(exact(40),loose(100));root.layout(0,0,40,root.getMeasuredHeight());check(root.getMeasuredHeight()==25&&first.getTop()+8==second.getTop()+3,"Top baseline alignment expands wrap height");
  root.setGravity(Gravity.BOTTOM);layout(root,40,40);check(first.getTop()+8==second.getTop()+3&&second.getBottom()==40,"Bottom baseline group aligns descents");
  root.setGravity(Gravity.CENTER_VERTICAL);layout(root,40,40);check(first.getTop()==14&&second.getTop()==10,"Center gravity keeps independent centering");
  root.setGravity(Gravity.TOP);root.setBaselineAligned(false);layout(root,40,40);check(first.getTop()==0&&second.getTop()==0,"Disabled baseline alignment");
  root.setBaselineAligned(true);second.setVisibility(View.GONE);root.measure(exact(40),loose(100));check(root.getMeasuredHeight()==12,"Gone baseline excluded from wrap height");
  second.setVisibility(View.VISIBLE);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);layout(root,40,40);check(first.getLeft()>second.getLeft()&&first.getTop()+8==second.getTop()+3,"RTL preserves baseline alignment");
 }
 public static void check(Context context){
  baselines(context);
  check(new VerticalOnly(context).getOrientation()==LinearLayout.VERTICAL,"subclass orientation without XML override");
  LinearLayout defaults=new LinearLayout(context,null,0);check(defaults.getOrientation()==LinearLayout.HORIZONTAL&&defaults.isBaselineAligned(),"absent attributes retain defaults");
  LinearLayout root=new LinearLayout(context);Leaf first=new Leaf(context,Color.RED,8,6),second=new Leaf(context,Color.BLUE,8,6);
  root.addView(first,new LinearLayout.LayoutParams(0,-1,1));root.addView(second,new LinearLayout.LayoutParams(0,-1,3));
  for(int repeat=0;repeat<3;repeat++)for(int orientation=0;orientation<2;orientation++){
   root.setOrientation(orientation);for(int i=0;i<2;i++){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)root.getChildAt(i).getLayoutParams();p.width=orientation==0?0:-1;p.height=orientation==0?-1:0;root.getChildAt(i).setLayoutParams(p);}
   int width=orientation==0?40:12,height=orientation==0?12:40;layout(root,width,height);
   check((orientation==0?first.getWidth():first.getHeight())==10&&(orientation==0?second.getLeft():second.getTop())==10,"weighted geometry after orientation change");
   Bitmap pixels=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);root.draw(new Canvas(pixels));
   check(pixels.getPixel(0,0)==Color.RED&&pixels.getPixel(width-1,height-1)==Color.BLUE,"weighted pixels");pixels.recycle();
   float x=orientation==0?15:3,y=orientation==0?3:15;
   for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent event=MotionEvent.obtain(1,2,action,x,y,0);try{check(root.dispatchTouchEvent(event),"weighted child input");}finally{event.recycle();}}
   check(second.x==(orientation==0?5:3)&&second.y==(orientation==0?3:5),"weighted input coordinates");
  }
  check(second.clicks==6,"clicks after repeated orientation changes");
  root.setOrientation(LinearLayout.HORIZONTAL);first.setLayoutParams(new LinearLayout.LayoutParams(10,5));second.setLayoutParams(new LinearLayout.LayoutParams(10,5));root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);layout(root,40,12);
  check(first.getLeft()==30&&second.getLeft()==20,"RTL child order and START gravity");
  root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);root.setGravity(Gravity.CENTER);layout(root,40,15);check(first.getLeft()==10&&first.getTop()==5&&second.getLeft()==20,"major and cross gravity");
  second.setVisibility(View.GONE);layout(root,40,15);check(first.getLeft()==15,"gone children excluded");second.setVisibility(View.VISIBLE);
  root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.TOP|Gravity.LEFT);root.setPadding(2,1,3,2);first.setLayoutParams(new LinearLayout.LayoutParams(13,5));second.setLayoutParams(new LinearLayout.LayoutParams(-1,7));root.measure(loose(100),loose(100));root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());
  check(root.getMeasuredWidth()==18&&root.getMeasuredHeight()==15&&second.getMeasuredWidth()==13,"wrap parent and uniform MATCH_PARENT cross size");
  LinearLayout.LayoutParams margins=new LinearLayout.LayoutParams(4,3);margins.setMargins(2,1,3,2);margins.gravity=Gravity.RIGHT;first.setLayoutParams(margins);layout(root,30,30);check(first.getLeft()==20&&first.getTop()==2&&second.getTop()==7,"margins and per-child gravity");
  LinearLayout shrink=new LinearLayout(context);Leaf a=new Leaf(context,Color.RED,10,2),b=new Leaf(context,Color.BLUE,10,2);shrink.addView(a,new LinearLayout.LayoutParams(10,2,1));shrink.addView(b,new LinearLayout.LayoutParams(10,2,1));layout(shrink,10,2);check(a.getWidth()==5&&b.getWidth()==5,"negative weighted remainder");
  shrink.setWeightSum(4);a.setLayoutParams(new LinearLayout.LayoutParams(0,2,1));b.setLayoutParams(new LinearLayout.LayoutParams(0,2,1));layout(shrink,20,2);check(a.getWidth()==5&&b.getWidth()==5,"explicit weight sum leaves space");
  try{root.setOrientation(2);throw new AssertionError("invalid orientation");}catch(IllegalArgumentException expected){}
  try{root.setWeightSum(Float.NaN);throw new AssertionError("nonfinite weights");}catch(IllegalArgumentException expected){}
  a.setLayoutParams(new LinearLayout.LayoutParams(0,2,-1));try{layout(shrink,10,2);throw new AssertionError("negative weight accepted");}catch(IllegalArgumentException expected){}
  shrink.setWeightSum(0);a.setLayoutParams(new LinearLayout.LayoutParams(0,2,Float.MAX_VALUE));b.setLayoutParams(new LinearLayout.LayoutParams(0,2,Float.MAX_VALUE));layout(shrink,10,2);check(a.getWidth()==5&&b.getWidth()==5,"large finite weights do not overflow");
  LinearLayout drawFlag=new LinearLayout(context){protected void onDraw(Canvas canvas){canvas.drawColor(Color.GREEN);}};drawFlag.setBackgroundColor(Color.BLUE);drawFlag.addView(new Leaf(context,Color.RED,2,2),new LinearLayout.LayoutParams(2,2));layout(drawFlag,6,4);
  drawFlag.setWillNotDraw(true);check(drawFlag.willNotDraw(),"drawing flag getter");Bitmap flagPixels=Bitmap.createBitmap(6,4,Bitmap.Config.ARGB_8888);drawFlag.draw(new Canvas(flagPixels));
  check(flagPixels.getPixel(0,0)==Color.RED&&flagPixels.getPixel(5,3)==Color.BLUE,"skip own draw preserves background and children");
  drawFlag.setWillNotDraw(false);drawFlag.draw(new Canvas(flagPixels));check(flagPixels.getPixel(0,0)==Color.RED&&flagPixels.getPixel(5,3)==Color.GREEN,"reenable own draw");flagPixels.recycle();
  System.out.println("SHIRAZ_FIXTURE_LINEARLAYOUT_OK");
 }
}
