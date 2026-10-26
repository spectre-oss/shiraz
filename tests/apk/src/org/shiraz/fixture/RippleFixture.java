// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
public final class RippleFixture {
 private static void solidStyle(RippleDrawable ripple){try{RippleDrawable.class.getMethod("setRippleStyle",int.class).invoke(ripple,0);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 private static RippleDrawable solid(ColorStateList color,Drawable content,Drawable mask){RippleDrawable ripple=new RippleDrawable(color,content,mask);solidStyle(ripple);return ripple;}
 public interface Clock {void at(long time,Runnable work);}
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private static Bitmap draw(Drawable d){Bitmap b=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);d.draw(new Canvas(b));return b;}
 private static int count(Bitmap b){int n=0;for(int y=0;y<40;y++)for(int x=0;x<40;x++)if((b.getPixel(x,y)>>>24)!=0)n++;return n;}
 private static final int[] PRESS={android.R.attr.state_enabled,android.R.attr.state_pressed},IDLE={android.R.attr.state_enabled};
 public static final class HotspotRipple extends RippleDrawable {float x,y;public HotspotRipple(){super(ColorStateList.valueOf(0xffff0000),null,null);}public void setHotspot(float x,float y){super.setHotspot(x,y);this.x=x;this.y=y;}}
 public static void immediate(Context context){
  Drawable mask=new ColorDrawable(0x80ffffff);LayerDrawable inset=new LayerDrawable(new Drawable[]{mask});inset.setLayerInset(0,10,10,10,10);
  RippleDrawable ripple=solid(ColorStateList.valueOf(0xffff0000),null,inset);ripple.setBounds(0,0,40,40);
  Bitmap b=draw(ripple);try{check(count(b)==0,"Mask must not draw as content");}finally{b.recycle();}
  ripple.setState(PRESS);ripple.jumpToCurrentState();b=draw(ripple);try{check((b.getPixel(20,20)>>>24)==128&&b.getPixel(5,5)==0,"Ripple alpha mask and clipped corners");}finally{b.recycle();}
  ripple.setState(IDLE);ripple.jumpToCurrentState();b=draw(ripple);try{check(count(b)==0,"Released ripple clears");}finally{b.recycle();}
  RippleDrawable content=solid(ColorStateList.valueOf(0xffff0000),new ColorDrawable(0xff0000ff),null);content.setBounds(0,0,40,40);b=draw(content);try{check(b.getPixel(20,20)==0xff0000ff,"Content displayed while idle");}finally{b.recycle();}
  content.setState(PRESS);content.jumpToCurrentState();b=draw(content);try{check(b.getPixel(20,20)==0xffff0000,"Ripple overlays content");}finally{b.recycle();}
  content.setState(new int[0]);b=draw(content);try{check(b.getPixel(20,20)==0xff0000ff,"Disabled state removes ripple");}finally{b.recycle();}
  RippleDrawable unbounded=solid(ColorStateList.valueOf(0xff00ff00),null,null);unbounded.setBounds(10,10,30,30);unbounded.setState(PRESS);unbounded.jumpToCurrentState();b=draw(unbounded);try{check((b.getPixel(20,7)>>>24)!=0,"Empty ripple can extend beyond bounds");}finally{b.recycle();}
  unbounded.setRadius(3);b=draw(unbounded);try{check(b.getPixel(20,7)==0&&b.getPixel(20,20)==0xff00ff00,"Explicit radius");}finally{b.recycle();}
  unbounded.setHotspotBounds(0,0,10,10);Rect bounds=new Rect();unbounded.getHotspotBounds(bounds);check(bounds.equals(new Rect(0,0,10,10)),"Hotspot bounds copy");
  int xml=context.getResources().getIdentifier("ripple_mask","drawable","org.shiraz.fixture");Drawable loaded=context.getResources().getDrawable(xml,context.getTheme());check(loaded instanceof RippleDrawable,"XML ripple type");solidStyle((RippleDrawable)loaded);loaded.setBounds(0,0,40,40);loaded.setState(PRESS);loaded.jumpToCurrentState();b=draw(loaded);try{check(b.getPixel(20,20)==0xff00ff00&&b.getPixel(1,1)==0,"XML state color and mask inset");}finally{b.recycle();}
  HotspotRipple touch=new HotspotRipple();View view=new View(context);view.layout(0,0,40,40);view.setBackground(touch);view.setClickable(true);MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,7,13,0);try{check(view.dispatchTouchEvent(down)&&touch.x==7&&touch.y==13,"View forwards touch hotspot");}finally{down.recycle();}touch.jumpToCurrentState();check(view.isPressed(),"View drives pressed state");view.setPressed(false);view.jumpDrawablesToCurrentState();
  ripple.setState(new int[]{android.R.attr.state_enabled,android.R.attr.state_focused});ripple.jumpToCurrentState();b=draw(ripple);try{check((b.getPixel(20,20)>>>24)>0,"Focus feedback");}finally{b.recycle();}ripple.setBounds(0,0,30,30);ripple.setState(PRESS);ripple.jumpToCurrentState();b=draw(ripple);try{check((b.getPixel(15,15)>>>24)==128&&b.getPixel(30,30)==0,"Mask buffers follow resized bounds");}finally{b.recycle();}
  // Compare the rectangular fast path with a generic opaque mask, including insets.
  Drawable generic=new Drawable(){public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}public void draw(Canvas c){Paint p=new Paint();p.setColor(0xffffffff);c.drawRect(getBounds(),p);}};
  RippleDrawable fast=solid(ColorStateList.valueOf(0x804488cc),null,new ColorDrawable(0xffffffff));
  RippleDrawable slow=solid(ColorStateList.valueOf(0x804488cc),null,generic);
  for(RippleDrawable d:new RippleDrawable[]{fast,slow}){d.setBounds(0,0,40,40);d.setLayerInset(0,5,7,9,3);d.setState(PRESS);d.jumpToCurrentState();}
  Bitmap a=draw(fast),z=draw(slow);try{for(int y=0;y<40;y++)for(int x=0;x<40;x++)check(a.getPixel(x,y)==z.getPixel(x,y),"Solid mask preserves inset and alpha");}finally{a.recycle();z.recycle();}
  final int[] maskDraws={0};Drawable counted=new Drawable(){public void setAlpha(int a){invalidateSelf();}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}public void draw(Canvas c){maskDraws[0]++;Paint p=new Paint();p.setColor(0x80ffffff);c.drawRect(getBounds(),p);}};
  RippleDrawable cached=solid(ColorStateList.valueOf(0xffff0000),null,counted);cached.setBounds(0,0,40,40);cached.setState(PRESS);cached.jumpToCurrentState();
  for(int i=0;i<3;i++){Bitmap sample=draw(cached);sample.recycle();}check(maskDraws[0]==1,"Static mask rasterized once");
  counted.setAlpha(128);Bitmap sample=draw(cached);sample.recycle();check(maskDraws[0]==2,"Child invalidation refreshes cached mask");
  System.out.println("RIPPLE_MASK_STATE_XML_OK");
 }
 public static void timed(final Context context,Clock clock){
  final RippleDrawable ripple=solid(ColorStateList.valueOf(0xffff0000),null,null);ripple.setBounds(0,0,40,40);ripple.setHotspot(5,20);final int[] scheduled={0},sizes={0,0};
  ripple.setCallback(new Drawable.Callback(){public void invalidateDrawable(Drawable who){}public void scheduleDrawable(Drawable who,Runnable r,long when){scheduled[0]++;}public void unscheduleDrawable(Drawable who,Runnable r){}});
  clock.at(1000,new Runnable(){public void run(){ripple.setState(PRESS);Bitmap b=draw(ripple);try{check(count(b)==0,"Ripple starts transparent");}finally{b.recycle();}check(scheduled[0]>0,"Frame requested");}});
  clock.at(1040,new Runnable(){public void run(){Bitmap b=draw(ripple);try{sizes[0]=count(b);check((b.getPixel(7,20)>>>24)>0&&b.getPixel(35,20)==0,"Ripple begins near hotspot");}finally{b.recycle();}}});
  clock.at(1250,new Runnable(){public void run(){Bitmap b=draw(ripple);try{sizes[1]=count(b);check(sizes[1]>sizes[0],"Ripple expands");}finally{b.recycle();}ripple.setState(IDLE);}});
  clock.at(1325,new Runnable(){public void run(){Bitmap b=draw(ripple);try{int a=b.getPixel(20,20)>>>24;check(a>0&&a<255,"Release fades");}finally{b.recycle();}}});
  clock.at(1450,new Runnable(){public void run(){int old=scheduled[0];Bitmap b=draw(ripple);try{check(count(b)==0,"Exit settles transparent");}finally{b.recycle();}check(scheduled[0]==old,"Idle does not request frames");ripple.setState(PRESS);ripple.setVisible(false,false);b=draw(ripple);try{check(count(b)==0,"Hidden ripple stops drawing");}finally{b.recycle();}}});
  clock.at(1600,new Runnable(){public void run(){ripple.setVisible(true,false);ripple.setState(PRESS);}});
  clock.at(1620,new Runnable(){public void run(){ripple.setState(IDLE);}});
  clock.at(1740,new Runnable(){public void run(){Bitmap b=draw(ripple);try{check((b.getPixel(20,20)>>>24)==255,"Quick tap remains visible during minimum hold");}finally{b.recycle();}}});
  clock.at(1900,new Runnable(){public void run(){Bitmap b=draw(ripple);try{int a=b.getPixel(20,20)>>>24;check(a>0&&a<255,"Quick tap fades after hold");}finally{b.recycle();}}});
  clock.at(2010,new Runnable(){public void run(){Bitmap b=draw(ripple);try{check(count(b)==0,"Quick tap completes");}finally{b.recycle();}}});
  final RippleDrawable row=solid(ColorStateList.valueOf(0xff4488cc),null,new ColorDrawable(0xffffffff));row.setBounds(0,0,720,72);row.setHotspot(180,36);
  clock.at(2100,new Runnable(){public void run(){row.setState(PRESS);}});
  clock.at(2116,new Runnable(){public void run(){Bitmap b=Bitmap.createBitmap(720,72,Bitmap.Config.ARGB_8888);try{row.draw(new Canvas(b));check((b.getPixel(180,36)>>>24)>0,"Wide row starts at click");check((b.getPixel(80,36)>>>24)>0&&b.getPixel(700,36)==0,"Android wide-row starting radius and clamped origin");}finally{b.recycle();}}});
  final RippleDrawable repeat=solid(ColorStateList.valueOf(0x80ff0000),null,null);repeat.setBounds(0,0,40,40);
  clock.at(2200,new Runnable(){public void run(){repeat.setState(PRESS);}});
  clock.at(2500,new Runnable(){public void run(){repeat.setState(IDLE);}});
  clock.at(2520,new Runnable(){public void run(){repeat.setState(PRESS);}});
  clock.at(2550,new Runnable(){public void run(){Bitmap b=draw(repeat);try{check((b.getPixel(20,20)>>>24)>100,"New press preserves fading wave");}finally{b.recycle();}}});
  final RippleDrawable hover=solid(ColorStateList.valueOf(0xffff0000),null,null);hover.setBounds(0,0,40,40);
  clock.at(2600,new Runnable(){public void run(){hover.setState(new int[]{android.R.attr.state_enabled,16843623});}});
  clock.at(2640,new Runnable(){public void run(){Bitmap b=draw(hover);try{int a=b.getPixel(20,20)>>>24;check(a>=25&&a<=26,"Hover animates to 20 percent over 80ms");}finally{b.recycle();}}});
  clock.at(2680,new Runnable(){public void run(){Bitmap b=draw(hover);try{check((b.getPixel(20,20)>>>24)==51,"Hover endpoint");}finally{b.recycle();}hover.setState(new int[]{android.R.attr.state_enabled,android.R.attr.state_focused});}});
  clock.at(2760,new Runnable(){public void run(){Bitmap b=draw(hover);try{check((b.getPixel(20,20)>>>24)==153,"Focus endpoint at 60 percent");}finally{b.recycle();}}});
  final RippleDrawable pattern=new RippleDrawable(ColorStateList.valueOf(0x800066ff),null,new ColorDrawable(0xffffffff));pattern.setBounds(0,0,40,40);pattern.setHotspot(20,20);
  clock.at(2800,new Runnable(){public void run(){pattern.setState(PRESS);}});
  clock.at(2840,new Runnable(){public void run(){pattern.setState(IDLE);}});
  clock.at(2920,new Runnable(){public void run(){Bitmap b=draw(pattern);try{int center=b.getPixel(20,20)>>>24,edge=b.getPixel(2,2)>>>24;check(center>edge&&center>0,"Patterned ripple has soft radial coverage");}finally{b.recycle();}}});
  clock.at(3250,new Runnable(){public void run(){Bitmap b=draw(pattern);try{check((b.getPixel(20,20)>>>24)>40,"Patterned quick tap finishes 450ms entry");}finally{b.recycle();}}});
  clock.at(3500,new Runnable(){public void run(){Bitmap b=draw(pattern);try{int a=b.getPixel(20,20)>>>24;check(a>0&&a<40,"Patterned release fades over 375ms");}finally{b.recycle();}}});
  clock.at(3625,new Runnable(){public void run(){Bitmap b=draw(pattern);try{check(count(b)==0,"Patterned ripple exit complete");}finally{b.recycle();}}});
  System.out.println("RIPPLE_ANIMATION_HOTSPOT_OK");
 }
}
