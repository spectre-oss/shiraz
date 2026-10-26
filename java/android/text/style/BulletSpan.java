// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
public class BulletSpan implements LeadingMarginSpan {
 public static final int STANDARD_GAP_WIDTH=2;
 private static final int RADIUS=4;
 private final int gap,color;
 private final boolean colored;
 public BulletSpan(){this(STANDARD_GAP_WIDTH);}
 public BulletSpan(int gap){this.gap=gap;color=0;colored=false;}
 public BulletSpan(int gap,int color){this.gap=gap;this.color=color;colored=true;}
 public int getLeadingMargin(boolean first){return 2*RADIUS+gap;}
 public int getGapWidth(){return gap;}
 public int getColor(){return color;}
 public void drawLeadingMargin(Canvas canvas,Paint paint,int x,int dir,int top,int baseline,int bottom,CharSequence text,int start,int end,boolean first,Layout layout){
  if(((android.text.Spanned)text).getSpanStart(this)!=start)return;
  int old=paint.getColor();Paint.Style style=paint.getStyle();
  if(colored)paint.setColor(color);paint.setStyle(Paint.Style.FILL);
  canvas.drawCircle(x+dir*RADIUS,(top+bottom)/2f,RADIUS,paint);
  paint.setColor(old);paint.setStyle(style);
 }
}
