// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.view.animation.AnimationUtils;
/** Solid ripple with AOSP-compatible timing and reusable software rendering state. */
public class RippleDrawable extends LayerDrawable {
 public boolean hasFocusStateSpecified(){return true;}
 public static final int RADIUS_AUTO=-1;
 private static final int MASK=0x0102002e;
 public static final int STYLE_SOLID=0,STYLE_PATTERNED=1;
 private int style=STYLE_PATTERNED;private ColorStateList effectColor=ColorStateList.valueOf(0x8dffffff);
 private Bitmap patternBuffer,patternMask;private int[] patternPixels,patternMaskPixels;private Canvas patternMaskCanvas;private final Rect patternBounds=new Rect();
 private boolean patternMaskDirty=true;private final Paint patternPaint=new Paint();
 private ColorStateList color;
 private int radius=RADIUS_AUTO,alpha=255;
 private final Rect hotspotBounds=new Rect();private boolean customBounds,hotspotSet,pressed,focused,hovered,scheduled;
 private static final android.view.animation.Interpolator MOTION=new android.view.animation.PathInterpolator(.4f,0,.2f,1);
 private float hotspotX,hotspotY,backgroundFrom,backgroundTo;private long backgroundTime;
 private static final class Wave {long enter,exit;float x,y;boolean active,released,settled;}
 private final Wave[] waves=new Wave[11];private Wave current;
 private boolean maskDirty=true;private Canvas maskCanvas,rippleCanvas;
 private final Paint ripplePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private Bitmap maskBuffer,rippleBuffer;private int[] maskPixels,ripplePixels;
 private final Runnable frame=new Runnable(){public void run(){scheduled=false;invalidateSelf();}};
 private static Drawable[] layers(Drawable content,Drawable mask){return content==null?(mask==null?new Drawable[0]:new Drawable[]{mask}):(mask==null?new Drawable[]{content}:new Drawable[]{content,mask});}
 public RippleDrawable(ColorStateList color,Drawable content,Drawable mask){this(color,layers(content,mask));if(mask!=null)setId(getNumberOfLayers()-1,MASK);}
 private RippleDrawable(ColorStateList color,Drawable[] layers){super(layers);for(int i=0;i<waves.length;i++)waves[i]=new Wave();setColor(color);setPaddingMode(PADDING_MODE_STACK);}
 public static RippleDrawable shirazCreate(ColorStateList color,Drawable[] layers){return new RippleDrawable(color,layers);}
 public void setColor(ColorStateList value){if(value==null)throw new IllegalArgumentException("Ripple color");color=value;invalidateSelf();}
 public void setRadius(int value){if(value< -1||value>8192)throw new IllegalArgumentException("Ripple radius");radius=value;invalidateSelf();}
 public int getRadius(){return radius;}
 public void setRippleStyle(int value){if(value!=STYLE_SOLID&&value!=STYLE_PATTERNED)throw new IllegalArgumentException("Ripple style");if(style!=value){style=value;clearWaves();stop();releaseBuffers();invalidateSelf();}}
 public void setEffectColor(ColorStateList value){if(value==null)throw new NullPointerException("effect color");effectColor=value;invalidateSelf();}
 public ColorStateList getEffectColor(){return effectColor;}
 private int enterDuration(){return style==STYLE_PATTERNED?450:225;}
 private int exitDuration(){return style==STYLE_PATTERNED?375:150;}

 @Override public void setHotspot(float x,float y){if(!Float.isFinite(x)||!Float.isFinite(y)||Math.abs(x)>1000000||Math.abs(y)>1000000)throw new IllegalArgumentException("Ripple hotspot");hotspotX=x;hotspotY=y;hotspotSet=true;invalidateSelf();}
 @Override public void setHotspotBounds(int left,int top,int right,int bottom){if(right<left||bottom<top||(long)right-left>16384||(long)bottom-top>16384)throw new IllegalArgumentException("Hotspot bounds");hotspotBounds.set(left,top,right,bottom);customBounds=true;invalidateSelf();}
 @Override public void getHotspotBounds(Rect out){out.set(customBounds?hotspotBounds:getBounds());}
 @Override protected void onBoundsChange(Rect bounds){super.onBoundsChange(bounds);releaseBuffers();}
 @Override public boolean isStateful(){return true;}
 private static boolean has(int[] state,int value){for(int s:state)if(s==value)return true;return false;}
 private long now(){return AnimationUtils.currentAnimationTimeMillis();}
 private float background(long time){return backgroundFrom+(backgroundTo-backgroundFrom)*Math.min(1,Math.max(0,(time-backgroundTime)/80f));}
 private void clearWaves(){for(Wave wave:waves)wave.active=false;current=null;}
 @Override protected boolean onStateChange(int[] state){
  super.onStateChange(state);maskDirty=patternMaskDirty=true;boolean enabled=has(state,16842910),down=enabled&&has(state,16842919);long time=now();
  boolean focus=enabled&&has(state,16842908)&&(focused||!down),hover=enabled&&has(state,16843623)&&(hovered||!down);
  if(focus!=focused||hover!=hovered){backgroundFrom=background(time);backgroundTo=focus?.6f:hover?.2f:0;backgroundTime=time;focused=focus;hovered=hover;if(!android.animation.ValueAnimator.areAnimatorsEnabled())backgroundFrom=backgroundTo;}
  if(!enabled){clearWaves();backgroundFrom=backgroundTo=0;stop();}
  else if(down&&!pressed){
   for(Wave wave:waves)if(wave.active&&wave.released&&time>=wave.exit+exitDuration())wave.active=false;
   current=null;for(Wave wave:waves)if(!wave.active){current=wave;break;}
   if(current!=null){Rect area=customBounds?hotspotBounds:getBounds();current.x=hotspotSet?hotspotX:area.left/2f+area.right/2f;current.y=hotspotSet?hotspotY:area.top/2f+area.bottom/2f;current.enter=time;current.active=true;current.released=false;current.settled=!android.animation.ValueAnimator.areAnimatorsEnabled();}
  }else if(!down&&pressed&&current!=null){if(hotspotSet){current.x=hotspotX;current.y=hotspotY;}current.exit=style==STYLE_PATTERNED?Math.max(time,current.enter+450):time>current.enter&&time<current.enter+225?current.enter+225:time;current.released=true;if(current.settled)current.active=false;current=null;}
  pressed=down;invalidateSelf();return true;
 }
 private void stop(){if(scheduled){unscheduleSelf(frame);scheduled=false;}}
 private void requestFrame(){if(getCallback()!=null&&!scheduled){scheduled=true;scheduleSelf(frame,Math.max(android.os.SystemClock.uptimeMillis(),now()+16));}}
 @Override public void jumpToCurrentState(){super.jumpToCurrentState();for(Wave wave:waves){if(wave.released)wave.active=false;else wave.settled=true;}backgroundFrom=backgroundTo;stop();invalidateSelf();}
 @Override public boolean setVisible(boolean visible,boolean restart){boolean changed=super.setVisible(visible,restart);if(!visible){pressed=false;clearWaves();focused=hovered=false;backgroundFrom=backgroundTo=0;stop();releaseBuffers();}else if(changed||restart){pressed=false;onStateChange(getState());}return changed;}
 @Override public void setAlpha(int value){if(value<0||value>255)throw new IllegalArgumentException("alpha");alpha=value;for(int i=0;i<getNumberOfLayers();i++)if(getId(i)!=MASK&&getDrawable(i)!=null)getDrawable(i).setAlpha(value);invalidateSelf();}
 @Override public int getAlpha(){return alpha;}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 private void releaseBuffers(){if(maskBuffer!=null)maskBuffer.recycle();if(rippleBuffer!=null)rippleBuffer.recycle();maskBuffer=rippleBuffer=null;maskPixels=ripplePixels=null;maskCanvas=rippleCanvas=null;maskDirty=patternMaskDirty=true;if(patternBuffer!=null)patternBuffer.recycle();if(patternMask!=null)patternMask.recycle();patternBuffer=patternMask=null;patternPixels=patternMaskPixels=null;patternMaskCanvas=null;}
 private void content(Canvas canvas){for(int i=0;i<getNumberOfLayers();i++)if(getId(i)!=MASK&&getDrawable(i)!=null)getDrawable(i).draw(canvas);}
 private void circle(Canvas canvas,float x,float y,float size,float opacity){if(size<=0||opacity<=0)return;Paint p=ripplePaint;int c=color.getColorForState(getState(),color.getDefaultColor());if(style==STYLE_PATTERNED&&(c>>>24)<128)c=(c&0xffffff)|0x80000000;int a=Math.round((c>>>24)*(alpha/255f)*opacity);p.setColor((c&0xffffff)|(Math.max(0,Math.min(255,a))<<24));p.setColorFilter(getColorFilter());canvas.drawCircle(x,y,size,p);}
 // A solid rectangular mask needs only a clip, not two full-size bitmap passes.
 // Restrict this to ColorDrawable: opaque custom drawables may still have holes.
 private boolean drawSolidMask(Canvas canvas,float x,float y,float size,float opacity){
  Drawable mask=findDrawableByLayerId(MASK);
  if(mask==null&&getNumberOfLayers()==1)mask=getDrawable(0);
  if(mask==null||mask.getClass()!=ColorDrawable.class||mask.getOpacity()!=PixelFormat.OPAQUE)return false;
  int saved=canvas.save();try{canvas.clipRect(getBounds());canvas.clipRect(mask.getBounds());circle(canvas,x,y,size,opacity);}finally{canvas.restoreToCount(saved);}return true;
 }
 @Override public void invalidateDrawable(Drawable who){maskDirty=patternMaskDirty=true;super.invalidateDrawable(who);}
 private void overlay(Canvas canvas,float x,float y,float size,float opacity){
   if(getNumberOfLayers()==0)circle(canvas,x,y,size,opacity);
   else if(!drawSolidMask(canvas,x,y,size,opacity)){Rect b=getBounds();int w=b.width(),h=b.height();if(w>0&&h>0){if(w>8192||h>8192||(long)w*h>4*1024*1024)throw new IllegalArgumentException("Ripple mask pixel limit");
     if(canvas.shirazIsGpuCanvas()){
      if(maskBuffer==null||maskBuffer.getWidth()!=w||maskBuffer.getHeight()!=h){if(maskBuffer!=null)maskBuffer.recycle();maskBuffer=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);maskDirty=true;}
      if(maskDirty){maskBuffer.eraseColor(0);Canvas mc=Canvas.shirazScreen(maskBuffer);mc.translate(-b.left,-b.top);Drawable mask=findDrawableByLayerId(MASK);if(mask!=null)mask.draw(mc);else content(mc);maskDirty=false;}
      int saved=canvas.saveLayer(new RectF(b),null);try{circle(canvas,x,y,size,opacity);Paint maskPaint=new Paint();maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));canvas.drawBitmap(maskBuffer,null,b,maskPaint);}finally{canvas.restoreToCount(saved);}return;
     }
     if(maskBuffer==null){maskBuffer=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);rippleBuffer=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);maskPixels=new int[w*h];ripplePixels=new int[w*h];maskCanvas=new Canvas(maskBuffer);rippleCanvas=new Canvas(rippleBuffer);maskCanvas.translate(-b.left,-b.top);rippleCanvas.translate(-b.left,-b.top);}
     if(maskDirty){maskBuffer.eraseColor(0);Drawable mask=findDrawableByLayerId(MASK);if(mask!=null)mask.draw(maskCanvas);else content(maskCanvas);maskBuffer.getPixels(maskPixels,0,w,0,0,w,h);maskDirty=false;}
     rippleBuffer.eraseColor(0);circle(rippleCanvas,x,y,size,opacity);rippleBuffer.getPixels(ripplePixels,0,w,0,0,w,h);
     for(int i=0;i<ripplePixels.length;i++){int a=((ripplePixels[i]>>>24)*(maskPixels[i]>>>24)+127)/255;ripplePixels[i]=(ripplePixels[i]&0xffffff)|(a<<24);}
     rippleBuffer.setPixels(ripplePixels,0,w,0,0,w,h);canvas.drawBitmap(rippleBuffer,null,b,null);
   }}
 }
 private void patterned(Canvas canvas,Wave wave,long time,Rect area,float cx,float cy,float r){
  float progress=wave.settled?.5f:.5f*MOTION.getInterpolation(Math.min(1,Math.max(0,(time-wave.enter)/450f)));
  if(wave.released&&time>=wave.exit)progress=.5f+.5f*Math.min(1,(time-wave.exit)/375f);
  if(progress<=0||progress>=1||r<=0)return;
  float origin=Math.min(1,progress*2),x=wave.x+(cx-wave.x)*origin,y=wave.y+(cy-wave.y)*origin;
  Rect bounds=getBounds();int left=bounds.left,top=bounds.top,right=bounds.right,bottom=bounds.bottom;
  if(getNumberOfLayers()==0){int extent=(int)Math.ceil(r);left=(int)cx-extent;top=(int)cy-extent;right=(int)cx+extent;bottom=(int)cy+extent;}
  int w=right-left,h=bottom-top;if(w<=0||h<=0)return;
  if(w>8192||h>8192||(long)w*h>4*1024*1024)throw new IllegalArgumentException("Ripple mask pixel limit");
  if(canvas.shirazIsGpuCanvas()){
   int c=color.getColorForState(getState(),color.getDefaultColor());if((c>>>24)<128)c=(c&0xffffff)|0x80000000;
   int sparkle=effectColor.getColorForState(getState(),effectColor.getDefaultColor());float phase=(float)wave.enter+Math.min(7000,Math.max(0,time-wave.enter))/214f;
   Drawable mask=findDrawableByLayerId(MASK);if(mask==null&&getNumberOfLayers()==1)mask=getDrawable(0);
   boolean solid=mask!=null&&mask.getClass()==ColorDrawable.class&&mask.getOpacity()==PixelFormat.OPAQUE;
   int saved=canvas.save();try{
    if(getNumberOfLayers()>0)canvas.clipRect(bounds);if(solid)canvas.clipRect(mask.getBounds());
    boolean masked=getNumberOfLayers()>0&&!solid;
    if(masked){
     if(patternMask==null||patternMask.getWidth()!=w||patternMask.getHeight()!=h||patternBounds.left!=left||patternBounds.top!=top){if(patternMask!=null)patternMask.recycle();patternMask=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);patternMaskDirty=true;patternBounds.set(left,top,right,bottom);}
     if(patternMaskDirty){patternMask.eraseColor(0);Canvas mc=Canvas.shirazScreen(patternMask);mc.translate(-left,-top);Drawable explicit=findDrawableByLayerId(MASK);if(explicit!=null)explicit.draw(mc);else content(mc);patternMaskDirty=false;}
     canvas.saveLayer(new RectF(left,top,right,bottom),null);
    }
    patternPaint.setAlpha(Math.round(alpha*((c>>>24)/255f)));patternPaint.setColorFilter(getColorFilter());
    canvas.shirazPatternRipple(new float[]{x,y,((int)r)*2.3f,progress,phase,Math.max(1,area.width()),Math.max(1,area.height()),cx,cy,r,0,0},c,sparkle,patternPaint);
    if(masked){Paint mp=new Paint();mp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));canvas.drawBitmap(patternMask,null,new Rect(left,top,right,bottom),mp);}
   }finally{canvas.restoreToCount(saved);}return;
  }
  if(patternBuffer==null||patternBounds.left!=left||patternBounds.top!=top||patternBounds.right!=right||patternBounds.bottom!=bottom){
   if(patternBuffer!=null)patternBuffer.recycle();if(patternMask!=null)patternMask.recycle();patternBounds.set(left,top,right,bottom);
   patternBuffer=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);patternPixels=new int[w*h];patternMask=null;patternMaskPixels=null;patternMaskCanvas=null;patternMaskDirty=true;
  }
  int c=color.getColorForState(getState(),color.getDefaultColor());if((c>>>24)<128)c=(c&0xffffff)|0x80000000;int sparkle=effectColor.getColorForState(getState(),effectColor.getDefaultColor());
  float phase=(float)wave.enter+Math.min(7000,Math.max(0,time-wave.enter))/214f;
  shiraz.runtime.NativePath.ripple(patternPixels,w,h,left,top,x,y,((int)r)*2.3f,progress,phase,Math.max(1,area.width()),Math.max(1,area.height()),c,sparkle,cx,cy,r);
  Drawable mask=findDrawableByLayerId(MASK);if(mask==null&&getNumberOfLayers()==1)mask=getDrawable(0);
  boolean solid=mask!=null&&mask.getClass()==ColorDrawable.class&&mask.getOpacity()==PixelFormat.OPAQUE;
  if(getNumberOfLayers()>0&&!solid){
   if(patternMask==null){patternMask=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);patternMaskPixels=new int[w*h];patternMaskCanvas=new Canvas(patternMask);patternMaskCanvas.translate(-left,-top);}
   if(patternMaskDirty){patternMask.eraseColor(0);Drawable explicit=findDrawableByLayerId(MASK);if(explicit!=null)explicit.draw(patternMaskCanvas);else content(patternMaskCanvas);patternMask.getPixels(patternMaskPixels,0,w,0,0,w,h);patternMaskDirty=false;}
   for(int i=0;i<patternPixels.length;i++)if((patternMaskPixels[i]>>>24)==0)patternPixels[i]=0;
  }
  patternBuffer.setPixels(patternPixels,0,w,0,0,w,h);patternPaint.setAlpha(Math.round(alpha*((c>>>24)/255f)));patternPaint.setColorFilter(getColorFilter());
  int saved=canvas.save();try{if(getNumberOfLayers()>0)canvas.clipRect(bounds);if(solid)canvas.clipRect(mask.getBounds());canvas.drawBitmap(patternBuffer,null,patternBounds,patternPaint);}finally{canvas.restoreToCount(saved);}
 }
 @Override public void draw(Canvas canvas){
  if(!isVisible())return;content(canvas);long time=now();Rect area=customBounds?hotspotBounds:getBounds();
  float cx=area.left/2f+area.right/2f,cy=area.top/2f+area.bottom/2f;
  float r=radius==RADIUS_AUTO?(float)Math.hypot(area.width()/2f,area.height()/2f):radius;
  float bg=background(time);boolean animating=backgroundFrom!=backgroundTo&&time<backgroundTime+80,visible=bg>0;
  if(bg>0)overlay(canvas,cx,cy,r,bg);
  for(Wave wave:waves){if(!wave.active)continue;
   if(wave.released&&time>=wave.exit+exitDuration()){wave.active=false;continue;}
   if(style==STYLE_PATTERNED){patterned(canvas,wave,time,area,cx,cy,r);visible=true;animating|=wave.released||!wave.settled&&time<wave.enter+450;continue;}
   float fraction=wave.settled?1:MOTION.getInterpolation(Math.min(1,Math.max(0,(time-wave.enter)/225f)));
   float opacity=wave.settled?1:Math.min(1,Math.max(0,(time-wave.enter)/75f));
   if(wave.released)opacity*=Math.max(0,1-Math.max(0,time-wave.exit)/150f);
   float start=Math.max(area.width(),area.height())*.3f;
   float hx=wave==current&&hotspotSet?hotspotX:wave.x,hy=wave==current&&hotspotSet?hotspotY:wave.y;
   float dx=hx-cx,dy=hy-cy,limit=r-start,distance=(float)Math.hypot(dx,dy);
   if(distance>Math.abs(limit)&&distance>0){hx=cx+dx*limit/distance;hy=cy+dy*limit/distance;}
   if(opacity>0){overlay(canvas,hx+(cx-hx)*fraction,hy+(cy-hy)*fraction,start+(r-start)*fraction,opacity);visible=true;}
   animating|=wave.released||!wave.settled&&time<wave.enter+225;
  }
  if(animating)requestFrame();else stop();if(!visible&&!animating)releaseBuffers();
 }
}
