// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;
/** Two adaptive layers with the host's circular icon mask. */
public class AdaptiveIconDrawable extends LayerDrawable {
 public AdaptiveIconDrawable(Drawable background,Drawable foreground){this(background,foreground,null);}
 public AdaptiveIconDrawable(Drawable background,Drawable foreground,Drawable monochrome){super(new Drawable[]{background,foreground,monochrome});}
 public Drawable getMonochrome(){return getDrawable(2);}
 public Drawable getBackground(){return getDrawable(0);}
 public Drawable getForeground(){return getDrawable(1);}
 public static float getExtraInsetFraction(){return .25f;}
 public Path getIconMask(){Rect b=getBounds();Path mask=new Path();mask.addOval(new RectF(b.left,b.top,b.right,b.bottom),Path.Direction.CW);return mask;}
 @Override protected void onBoundsChange(Rect b){int x=Math.round(b.width()*.25f),y=Math.round(b.height()*.25f);for(int i=0;i<3;i++){Drawable child=getDrawable(i);if(child!=null)child.setBounds(b.left-x,b.top-y,b.right+x,b.bottom+y);}}
 @Override public void draw(Canvas canvas){if(getBounds().isEmpty())return;int saved=canvas.save();try{canvas.clipPath(getIconMask());for(int i=0;i<2;i++){Drawable child=getDrawable(i);if(child!=null)child.draw(canvas);}}finally{canvas.restoreToCount(saved);}}
 private int intrinsic(boolean width){int size=-1;for(int i=0;i<2;i++){Drawable child=getDrawable(i);if(child!=null)size=Math.max(size,width?child.getIntrinsicWidth():child.getIntrinsicHeight());}return size<0?-1:Math.round(size/1.5f);}
 @Override public int getIntrinsicWidth(){return intrinsic(true);}
 @Override public int getIntrinsicHeight(){return intrinsic(false);}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 @Override public boolean getPadding(Rect out){out.set(0,0,0,0);return false;}
 @Override public void getOutline(Outline outline){outline.setOval(getBounds());outline.setAlpha(getAlpha()/255f);}
}
