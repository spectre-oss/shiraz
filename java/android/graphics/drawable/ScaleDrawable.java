// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;import android.view.Gravity;
/** Level-dependent child bounds; pixels are rendered at the resulting size. */
public class ScaleDrawable extends DrawableWrapper {
 private final int gravity;private final float widthScale,heightScale;private boolean intrinsicMinimum,ready;
 public ScaleDrawable(Drawable child,int gravity,float width,float height){super(child);if(!Float.isFinite(width)||!Float.isFinite(height)||Math.abs(width)>16||Math.abs(height)>16)throw new IllegalArgumentException("Scale fraction bound");this.gravity=gravity;widthScale=width;heightScale=height;ready=true;onBoundsChange(getBounds());}
 public void shirazSetIntrinsicMinimum(boolean value){intrinsicMinimum=value;onBoundsChange(getBounds());invalidateSelf();}
 @Override public void setDrawable(Drawable child){super.setDrawable(child);if(ready)onBoundsChange(getBounds());}
 private int scaled(long extent,int intrinsic,float scale){if(extent<0||extent>Integer.MAX_VALUE)throw new IllegalArgumentException("Scale bounds");if(scale<=0)return (int)extent;double result=extent-(long)((extent-(intrinsicMinimum?intrinsic:0))*(10000L-getLevel())*scale/10000.0);if(result<Integer.MIN_VALUE||result>Integer.MAX_VALUE)throw new IllegalArgumentException("Scaled extent overflow");return (int)result;}
 @Override protected void onBoundsChange(Rect bounds){Drawable child=getDrawable();if(child==null||!ready)return;int w=scaled((long)bounds.right-bounds.left,child.getIntrinsicWidth(),widthScale),h=scaled((long)bounds.bottom-bounds.top,child.getIntrinsicHeight(),heightScale);Rect target=new Rect();Gravity.apply(gravity,Math.max(0,w),Math.max(0,h),bounds,target,getLayoutDirection());child.setBounds(target);}
 @Override protected boolean onLevelChange(int level){super.onLevelChange(level);onBoundsChange(getBounds());invalidateSelf();return true;}
 @Override public boolean onLayoutDirectionChanged(int direction){super.onLayoutDirectionChanged(direction);onBoundsChange(getBounds());invalidateSelf();return true;}
 @Override public void draw(Canvas canvas){Drawable child=getDrawable();if(child!=null&&child.getLevel()!=0&&!child.getBounds().isEmpty())child.draw(canvas);}
 @Override public int getOpacity(){Drawable child=getDrawable();if(child==null||child.getLevel()==0||child.getBounds().isEmpty())return PixelFormat.TRANSPARENT;int opacity=child.getOpacity();return opacity==PixelFormat.OPAQUE&&child.getLevel()<10000?PixelFormat.TRANSLUCENT:opacity;}
}
