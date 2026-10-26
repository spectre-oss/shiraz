// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
/** Texture consumer composed in the view tree through the host native-window bridge. */
public class TextureView extends View {
 public interface SurfaceTextureListener {
  void onSurfaceTextureAvailable(SurfaceTexture surface,int width,int height);
  void onSurfaceTextureSizeChanged(SurfaceTexture surface,int width,int height);
  boolean onSurfaceTextureDestroyed(SurfaceTexture surface);
  void onSurfaceTextureUpdated(SurfaceTexture surface);
 }
 private SurfaceTexture texture;private SurfaceTextureListener listener;private final Matrix transform=new Matrix();
 private boolean opaque=true,available,polling;private long frame=-1;private int width,height;
 private final Runnable poll=new Runnable(){public void run(){polling=false;if(!available||!isAttachedToWindow()||getVisibility()!=VISIBLE)return;SurfaceTexture current=texture;long next=current.shirazSurface().shirazFrameNumber();if(next!=frame){frame=next;current.shirazFrameAvailable();if(available&&texture==current&&listener!=null)listener.onSurfaceTextureUpdated(current);invalidate();}schedule();}};
 public TextureView(Context context){super(context);}
 public TextureView(Context context,AttributeSet attrs){super(context,attrs);}
 public TextureView(Context context,AttributeSet attrs,int style){super(context,attrs,style);}
 public TextureView(Context context,AttributeSet attrs,int style,int resource){super(context,attrs,style,resource);}
 public boolean isAvailable(){return available;}
 public SurfaceTexture getSurfaceTexture(){return texture;}
 public SurfaceTextureListener getSurfaceTextureListener(){return listener;}
 public void setSurfaceTextureListener(SurfaceTextureListener value){listener=value;}
 public void setOpaque(boolean value){opaque=value;invalidate();}
 @Override public boolean isOpaque(){return opaque;}
 public void setTransform(Matrix value){if(value==null)transform.reset();else{if(!value.isAffine())throw new UnsupportedOperationException("Perspective texture transform");transform.set(value);}invalidate();}
 public Matrix getTransform(Matrix out){if(out==null)out=new Matrix();out.set(transform);return out;}
 private void schedule(){if(!polling&&available&&isAttachedToWindow()&&getVisibility()==VISIBLE){polling=true;postDelayed(poll,16);}}
 private void update(){if(!isAttachedToWindow()||getWidth()<=0||getHeight()<=0)return;boolean created=!available;
  if(texture==null)texture=new SurfaceTexture(false);if(created||width!=getWidth()||height!=getHeight()){width=getWidth();height=getHeight();texture.setDefaultBufferSize(width,height);if(created)frame=texture.shirazSurface().shirazFrameNumber();available=true;if(listener!=null){if(created)listener.onSurfaceTextureAvailable(texture,width,height);else listener.onSurfaceTextureSizeChanged(texture,width,height);}}schedule();}
 public void setSurfaceTexture(SurfaceTexture value){if(value==null||value.isReleased())throw new IllegalArgumentException("Live SurfaceTexture required");if(value==texture)throw new IllegalArgumentException("SurfaceTexture already assigned");if(texture!=null)texture.release();texture=value;available=isAttachedToWindow();frame=-1;if(getWidth()>0&&getHeight()>0)value.setDefaultBufferSize(getWidth(),getHeight());schedule();invalidate();}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();update();}
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);update();}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(visibility==VISIBLE)schedule();else{removeCallbacks(poll);polling=false;}}
 @Override protected void onDetachedFromWindow(){removeCallbacks(poll);polling=false;SurfaceTexture old=texture;texture=null;available=false;frame=-1;try{if(old!=null&&(listener==null||listener.onSurfaceTextureDestroyed(old)))old.release();}finally{super.onDetachedFromWindow();}}
 @Override protected void shirazDrawSurface(Canvas canvas){if(!available||texture==null)return;int save=canvas.save();try{canvas.clipRect(0,0,getWidth(),getHeight());canvas.concat(transform);texture.shirazSurface().shirazDraw(canvas,getWidth(),getHeight());}finally{canvas.restoreToCount(save);}}
 public Canvas lockCanvas(){return lockCanvas(null);}
 public Canvas lockCanvas(Rect dirty){return available?texture.shirazSurface().lockCanvas(dirty):null;}
 public void unlockCanvasAndPost(Canvas canvas){if(!available)throw new IllegalStateException("TextureView unavailable");texture.shirazSurface().unlockCanvasAndPost(canvas);invalidate();}
 public Bitmap getBitmap(){return getBitmap(getWidth(),getHeight());}
 public Bitmap getBitmap(int w,int h){if(!available||w<=0||h<=0)return null;Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);return getBitmap(bitmap);}
 public Bitmap getBitmap(Bitmap bitmap){if(bitmap==null||!available)return bitmap;Canvas canvas=new Canvas(bitmap);canvas.scale((float)bitmap.getWidth()/getWidth(),(float)bitmap.getHeight()/getHeight());shirazDrawSurface(canvas);canvas.setBitmap(null);return bitmap;}
}
