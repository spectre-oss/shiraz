// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;
import android.graphics.drawable.shapes.Shape;
import android.content.res.ColorStateList;
public class ShapeDrawable extends Drawable {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private Shape shape;private int width,height,alpha=255;private Rect padding;
 private ColorStateList tint;private PorterDuff.Mode tintMode=PorterDuff.Mode.SRC_IN;
 public ShapeDrawable(){}public ShapeDrawable(Shape shape){setShape(shape);}
 public Paint getPaint(){return paint;}public Shape getShape(){return shape;}
 public void setShape(Shape value){shape=value;resize();invalidateSelf();}
 private void resize(){if(shape!=null)shape.resize(Math.max(0,getBounds().width()),Math.max(0,getBounds().height()));}
 protected void onBoundsChange(Rect bounds){resize();}
 public void setIntrinsicWidth(int value){width=value;invalidateSelf();}public int getIntrinsicWidth(){return width;}
 public void setIntrinsicHeight(int value){height=value;invalidateSelf();}public int getIntrinsicHeight(){return height;}
 public void setPadding(int l,int t,int r,int b){padding=new Rect(l,t,r,b);invalidateSelf();}public void setPadding(Rect value){padding=value==null?null:new Rect(value);invalidateSelf();}
 public boolean getPadding(Rect out){if(padding==null){out.setEmpty();return false;}out.set(padding);return true;}
 public void setAlpha(int value){if(value<0||value>255)throw new IllegalArgumentException("alpha");alpha=value;invalidateSelf();}public int getAlpha(){return alpha;}
 public void setColorFilter(ColorFilter value){paint.setColorFilter(value);invalidateSelf();}public ColorFilter getColorFilter(){return paint.getColorFilter();}
 public void setDither(boolean value){paint.setDither(value);invalidateSelf();}
 public void setTintList(ColorStateList value){tint=value;refreshTintOwner();}public void setTintMode(PorterDuff.Mode value){tintMode=value;invalidateSelf();}
 public boolean isStateful(){return tint!=null&&tint.isStateful();}protected boolean onStateChange(int[] state){if(!isStateful())return false;invalidateSelf();return true;}
 public void getOutline(Outline out){if(shape==null)out.setRect(getBounds());else{shape.getOutline(out);out.offset(getBounds().left,getBounds().top);}out.setAlpha(paint.getAlpha()*alpha/65025f);}
 public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 protected void onDraw(Shape shape,Canvas canvas,Paint paint){shape.draw(canvas,paint);}
 public void draw(Canvas canvas){Rect b=getBounds();if(b.isEmpty())return;Paint drawing=new Paint(paint);drawing.setAlpha(paint.getAlpha()*alpha/255);if(drawing.getColorFilter()==null&&tint!=null&&tintMode!=null)drawing.setColorFilter(new PorterDuffColorFilter(tint.getColorForState(getState(),tint.getDefaultColor()),tintMode));int saved=canvas.save();try{canvas.translate(b.left,b.top);if(shape==null)canvas.drawRect(0,0,b.width(),b.height(),drawing);else onDraw(shape,canvas,drawing);}finally{canvas.restoreToCount(saved);}}
}
