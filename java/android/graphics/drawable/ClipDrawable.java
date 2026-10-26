// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;
import android.view.Gravity;
/** Level-based clipping of a child's unchanged bounds. */
public class ClipDrawable extends DrawableWrapper {
 public static final int HORIZONTAL=1,VERTICAL=2;
 private final int gravity,orientation;
 public ClipDrawable(Drawable drawable,int gravity,int orientation){super(drawable);if(orientation<0||orientation>3)throw new IllegalArgumentException("Clip orientation");this.gravity=gravity;this.orientation=orientation;}
 @Override public void draw(Canvas canvas){Drawable child=getDrawable();int level=Math.max(0,Math.min(10000,getLevel()));if(child==null||level==0)return;Rect bounds=getBounds();long w=(long)bounds.right-bounds.left,h=(long)bounds.bottom-bounds.top;if(w<=0||h<=0)return;if(w>Integer.MAX_VALUE||h>Integer.MAX_VALUE)throw new IllegalArgumentException("Clip bounds");int width=(int)((orientation&HORIZONTAL)==0?w:w-(w*(10000-level)/10000)),height=(int)((orientation&VERTICAL)==0?h:h-(h*(10000-level)/10000));Rect clip=new Rect();Gravity.apply(gravity,width,height,bounds,clip,getLayoutDirection());int save=canvas.save();try{if(canvas.clipRect(clip))child.draw(canvas);}finally{canvas.restoreToCount(save);}}
 @Override public boolean onLayoutDirectionChanged(int direction){super.onLayoutDirectionChanged(direction);invalidateSelf();return true;}
 @Override protected boolean onLevelChange(int level){super.onLevelChange(level);invalidateSelf();return true;}
 @Override public int getOpacity(){Drawable child=getDrawable();if(child==null||getLevel()<=0||child.getOpacity()==PixelFormat.TRANSPARENT)return PixelFormat.TRANSPARENT;return getLevel()>=10000?child.getOpacity():PixelFormat.TRANSLUCENT;}
}
