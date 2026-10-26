// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
import android.util.AttributeSet;
import android.graphics.*;
import java.util.ArrayList;
/** Software surface composed at this view's position; native compositor Z ordering is unsupported. */
public class SurfaceView extends View {
 private final Thread ui=Thread.currentThread();private final Holder holder=new Holder();
 private int fixedWidth=-1,fixedHeight=-1,format=PixelFormat.RGB_565,currentWidth,currentHeight,currentFormat;
 private volatile boolean active,creating;private boolean updating,pending;
 public SurfaceView(Context context){super(context);}
 public SurfaceView(Context context,AttributeSet attrs){super(context,attrs);}
 public SurfaceView(Context context,AttributeSet attrs,int style){super(context,attrs,style);}
 public SurfaceView(Context context,AttributeSet attrs,int style,int resource){super(context,attrs,style,resource);}
 public SurfaceHolder getHolder(){return holder;}
 private void owner(){if(Thread.currentThread()!=ui)throw new IllegalStateException("Surface lifecycle must run on the view thread");}
 private boolean polling;private long frame=-1;
 private final Runnable poll=new Runnable(){public void run(){polling=false;if(!active||!isAttachedToWindow()||!isShown())return;long next=holder.surface.shirazFrameNumber();if(next!=frame){frame=next;invalidate();}schedule();}};
 private void schedule(){if(!polling&&active&&isAttachedToWindow()&&isShown()){polling=true;postDelayed(poll,16);}}
 private boolean wanted(){return isShown()&&!shirazIsDetaching()&&getWidth()>0&&getHeight()>0;}
 private void update(){owner();if(updating){pending=true;return;}updating=true;try{int passes=0;do{pending=false;if(++passes>32)throw new IllegalStateException("Reentrant surface lifecycle limit");updateOnce();}while(pending);}finally{updating=false;}}
 private void updateOnce(){
  if(!wanted()){destroy();return;}
  int w=fixedWidth>0?fixedWidth:getWidth(),h=fixedHeight>0?fixedHeight:getHeight();
  if(active&&w==currentWidth&&h==currentHeight&&format==currentFormat){schedule();return;}
  boolean created=!active;holder.surface.shirazConfigure(w,h,format);currentWidth=w;currentHeight=h;currentFormat=format;active=true;
  ArrayList<SurfaceHolder.Callback> callbacks=holder.snapshot();creating=created;
  try{
   if(created)for(SurfaceHolder.Callback callback:callbacks){if(!wanted())break;callback.surfaceCreated(holder);}
   if(wanted())for(SurfaceHolder.Callback callback:callbacks){if(!wanted())break;callback.surfaceChanged(holder,currentFormat,currentWidth,currentHeight);}
   if(wanted())for(SurfaceHolder.Callback callback:callbacks)if(callback instanceof SurfaceHolder.Callback2)((SurfaceHolder.Callback2)callback).surfaceRedrawNeeded(holder);
  }finally{creating=false;if(!wanted())destroy();}
  schedule();invalidate();
 }
 private void destroy(){removeCallbacks(poll);polling=false;frame=-1;if(!active)return;active=false;creating=false;holder.surface.shirazStopProducer();try{for(SurfaceHolder.Callback callback:holder.snapshot())callback.surfaceDestroyed(holder);}finally{holder.surface.release();}}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();update();}
 @Override protected void onDetachedFromWindow(){try{destroy();}finally{super.onDetachedFromWindow();}}
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);update();}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);update();}
 @Override protected void onMeasure(int widthSpec,int heightSpec){if(fixedWidth>0)setMeasuredDimension(resolveSize(fixedWidth,widthSpec),resolveSize(fixedHeight,heightSpec));else super.onMeasure(widthSpec,heightSpec);}
 @Override protected void shirazDrawSurface(Canvas canvas){holder.surface.shirazDraw(canvas,getWidth(),getHeight());}
 public void setZOrderOnTop(boolean onTop){if(onTop)throw new UnsupportedOperationException("Surface Z ordering outside the view hierarchy");}
 public void setZOrderMediaOverlay(boolean overlay){if(overlay)throw new UnsupportedOperationException("Media surface overlay ordering");}
 private final class Holder implements SurfaceHolder {
  final Surface surface=new Surface();private final ArrayList<Callback> callbacks=new ArrayList<Callback>();
  synchronized ArrayList<Callback> snapshot(){return new ArrayList<Callback>(callbacks);}
  public synchronized void addCallback(Callback callback){if(callback==null)throw new NullPointerException("callback");if(callbacks.contains(callback))return;if(callbacks.size()>=1024)throw new IllegalStateException("Surface callback limit");callbacks.add(callback);}
  public synchronized void removeCallback(Callback callback){callbacks.remove(callback);}
  public boolean isCreating(){return creating;}
  public void setType(int type){if(type<0||type>3)throw new IllegalArgumentException("Surface type");}
  public void setFixedSize(int width,int height){owner();if(width<1||height<1||width>8192||height>8192||(long)width*height>16*1024*1024)throw new IllegalArgumentException("Surface dimensions");fixedWidth=width;fixedHeight=height;requestLayout();update();}
  public void setSizeFromLayout(){owner();fixedWidth=fixedHeight=-1;requestLayout();update();}
  public void setFormat(int value){owner();if(value!=PixelFormat.RGB_565&&value!=PixelFormat.RGBA_8888&&value!=PixelFormat.RGBX_8888&&value!=PixelFormat.OPAQUE&&value!=PixelFormat.TRANSLUCENT&&value!=PixelFormat.TRANSPARENT)throw new UnsupportedOperationException("Surface pixel format "+value);format=value;update();}
  public void setKeepScreenOn(boolean on){SurfaceView.this.setKeepScreenOn(on);}
  public Canvas lockCanvas(){return lockCanvas(null);}public Canvas lockCanvas(Rect dirty){return surface.shirazLock(dirty,true);}
  public Canvas lockHardwareCanvas(){return surface.lockHardwareCanvas();}
  public void unlockCanvasAndPost(Canvas canvas){surface.unlockCanvasAndPost(canvas);postInvalidate();}
  public Rect getSurfaceFrame(){return active?new Rect(0,0,currentWidth,currentHeight):new Rect();}
  public Surface getSurface(){return surface;}
 }
}
