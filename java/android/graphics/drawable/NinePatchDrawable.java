// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;
import android.content.res.*;
/** Density-aware software nine-patch drawable. */
public class NinePatchDrawable extends Drawable {
 private final NinePatch patch;
 private final Rect padding=new Rect();
 private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
 private int density=160;
 private ColorStateList tint;
 private PorterDuff.Mode mode=PorterDuff.Mode.SRC_IN;
 private ColorFilter filter;
 private boolean mirrored;
 private float baseAlpha=1;
 public NinePatchDrawable shirazCopySource(Resources resources){NinePatchDrawable copy=new NinePatchDrawable(resources,patch);copy.padding.set(padding);copy.setChangingConfigurations(getChangingConfigurations());return copy;}
 public void shirazSetBaseAlpha(float value){if(!Float.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("Nine-patch alpha");baseAlpha=value;invalidateSelf();}
 public void setAutoMirrored(boolean value){if(mirrored!=value){mirrored=value;invalidateSelf();}}
 public boolean isAutoMirrored(){return mirrored;}
 public boolean onLayoutDirectionChanged(int direction){if(mirrored){invalidateSelf();return true;}return false;}
 public NinePatchDrawable(Bitmap bitmap,byte[] chunk,Rect padding,String name){this(null,bitmap,chunk,padding,name);}
 public NinePatchDrawable(Resources resources,Bitmap bitmap,byte[] chunk,Rect padding,String name){this(resources,new NinePatch(bitmap,chunk,name));if(padding!=null)this.padding.set(padding);}
 public NinePatchDrawable(NinePatch patch){this(null,patch);}
 public NinePatchDrawable(Resources resources,NinePatch patch){if(patch==null)throw new NullPointerException("patch");this.patch=patch;patch.shirazPadding(padding);if(resources!=null)density=resources.getDisplayMetrics().densityDpi;}
 public Paint getPaint(){return paint;}
 public void setTargetDensity(int value){if(value<0||value>65535)throw new IllegalArgumentException("Density");density=value==0?160:value;invalidateSelf();}
 public void setTargetDensity(android.util.DisplayMetrics value){setTargetDensity(value.densityDpi);}public void setTargetDensity(Canvas value){setTargetDensity(value.getDensity());}
 private int scale(int value){int source=patch.getDensity();return source==0?value:(int)(((long)value*density+source/2)/source);}
 public int getIntrinsicWidth(){return scale(patch.getWidth());}public int getIntrinsicHeight(){return scale(patch.getHeight());}
 public boolean getPadding(Rect out){boolean flip=mirrored&&getLayoutDirection()==1;out.set(scale(flip?padding.right:padding.left),scale(padding.top),scale(flip?padding.left:padding.right),scale(padding.bottom));return out.left!=0||out.top!=0||out.right!=0||out.bottom!=0;}
 public void draw(Canvas canvas){
  Paint effective=paint;if(baseAlpha!=1){effective=new Paint(paint);effective.setAlpha(Math.round(paint.getAlpha()*baseAlpha));}
  if(!mirrored||getLayoutDirection()!=1){patch.shirazDraw(canvas,new RectF(getBounds()),effective,density);return;}
  Rect bounds=getBounds();int w=bounds.width(),h=bounds.height();if(w<=0||h<=0)return;
  if(w>8192||h>8192||(long)w*h>4L*1024*1024)throw new IllegalArgumentException("Nine-patch mirror bitmap bound");
  Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
  try{patch.shirazDraw(new Canvas(image),new RectF(0,0,w,h),effective,density);int[] pixels=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);for(int y=0;y<h;y++)for(int x=0;x<w/2;x++){int a=y*w+x,b=y*w+w-1-x,value=pixels[a];pixels[a]=pixels[b];pixels[b]=value;}image.setPixels(pixels,0,w,0,0,w,h);canvas.drawBitmap(image,null,bounds,null);}finally{image.recycle();}
 }
 public void setAlpha(int value){paint.setAlpha(value);invalidateSelf();}public int getAlpha(){return paint.getAlpha();}
 public void setColorFilter(ColorFilter value){ColorFilter.shirazValidate(value);filter=value;updateTint();}public ColorFilter getColorFilter(){return filter;}
 private void updateTint(){paint.setColorFilter(filter!=null?filter:tint==null||mode==null?null:new PorterDuffColorFilter(tint.getColorForState(getState(),tint.getDefaultColor()),mode));invalidateSelf();}
 public void setTintList(ColorStateList value){tint=value;updateTint();refreshTintOwner();}public void setTintMode(PorterDuff.Mode value){mode=value;updateTint();}
 public boolean isStateful(){return tint!=null&&tint.isStateful();}protected boolean onStateChange(int[] state){if(tint==null)return false;updateTint();return true;}
 public void setDither(boolean value){paint.setDither(value);invalidateSelf();}public void setFilterBitmap(boolean value){paint.setFilterBitmap(value);invalidateSelf();}
 public int getOpacity(){return getAlpha()==0&&paint.getColorFilter()==null?PixelFormat.TRANSPARENT:PixelFormat.TRANSLUCENT;}
}
