// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.graphics.Rect;
import android.view.Gravity;
/** Geometry for an anchored popup inside the host app window, in one coordinate space.
 * Content must already be measured. Parent scrolling and window input are separate.
 */
public final class PopupPlacement {
 private PopupPlacement(){}
 public static final class Result {
  public final Rect bounds;public final boolean above;
  private Result(Rect bounds,boolean above){this.bounds=bounds;this.above=above;}
 }
 private static long width(Rect r){return (long)r.right-r.left;}
 private static long height(Rect r){return (long)r.bottom-r.top;}
 private static void valid(Rect r){if(r==null)throw new NullPointerException("bounds");if(width(r)<0||height(r)<0)throw new IllegalArgumentException("Inverted bounds");}
 private static int integer(long value){if(value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)throw new IllegalArgumentException("Popup coordinate overflow");return (int)value;}
 public static Result dropdown(Rect anchor,Rect viewport,int measuredWidth,int measuredHeight,int xOffset,int yOffset,int gravity,int direction,boolean overlap,boolean clip){
  valid(anchor);valid(viewport);
  if(measuredWidth<0||measuredHeight<0)throw new IllegalArgumentException("Popup must be measured");
  if(direction!=0&&direction!=1)throw new IllegalArgumentException("Layout direction");
  long w=measuredWidth,h=measuredHeight;
  long x=(long)anchor.left+xOffset;
  if((Gravity.getAbsoluteGravity(gravity,direction)&Gravity.HORIZONTAL_GRAVITY_MASK)==Gravity.RIGHT)x+=width(anchor)-w;
  long below=(long)(overlap?anchor.top:anchor.bottom)+yOffset;
  long above=(long)anchor.top+yOffset-h;
  long y=below;
  if(below<viewport.top||below+h>viewport.bottom){
   if(above>=viewport.top&&above+h<=viewport.bottom)y=above;
  }
  if(clip){
   w=Math.min(w,width(viewport));h=Math.min(h,height(viewport));
   x=Math.max(viewport.left,Math.min(x,(long)viewport.right-w));
   y=Math.max(viewport.top,Math.min(y,(long)viewport.bottom-h));
  }
  return new Result(new Rect(integer(x),integer(y),integer(x+w),integer(y+h)),y<anchor.top);
 }
 public static int availableHeight(Rect anchor,Rect viewport,int yOffset,boolean overlap){
  valid(anchor);valid(viewport);
  long below=(long)viewport.bottom-(overlap?anchor.top:anchor.bottom)-yOffset;
  long above=(long)anchor.top-viewport.top+yOffset;
  return integer(Math.max(0,Math.max(above,below)));
 }
}
