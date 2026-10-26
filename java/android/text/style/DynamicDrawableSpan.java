// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
/** Inline drawable measured at its bounds and drawn on the line baseline or bottom. */
public abstract class DynamicDrawableSpan extends ReplacementSpan {
 public static final int ALIGN_BOTTOM=0,ALIGN_BASELINE=1,ALIGN_CENTER=2;
 protected final int verticalAlignment;
 public DynamicDrawableSpan(){this(ALIGN_BOTTOM);}
 protected DynamicDrawableSpan(int verticalAlignment){this.verticalAlignment=verticalAlignment;}
 public int getVerticalAlignment(){return verticalAlignment;}
 public abstract Drawable getDrawable();
 public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics){
  Drawable d=getDrawable();if(d==null)return 0;Rect r=d.getBounds();
  if(metrics!=null){metrics.ascent=-r.bottom;metrics.descent=0;metrics.top=-r.bottom;metrics.bottom=0;}
  return r.right;
 }
 public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int baseline,int bottom,Paint paint){
  Drawable d=getDrawable();if(d==null)return;
  canvas.save();
  int transY=bottom-d.getBounds().bottom;
  if(verticalAlignment==ALIGN_BASELINE)transY=baseline-d.getBounds().bottom;
  else if(verticalAlignment==ALIGN_CENTER)transY=(bottom-top-d.getBounds().height())/2+top;
  canvas.translate(x,transY);d.draw(canvas);canvas.restore();
 }
}
