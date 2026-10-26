// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
/** Paragraph margin contract; StaticLayout supports zero-width drawing callbacks. */
public interface LeadingMarginSpan extends ParagraphStyle {
 int getLeadingMargin(boolean first);
 void drawLeadingMargin(Canvas canvas,Paint paint,int x,int dir,int top,int baseline,int bottom,CharSequence text,int start,int end,boolean first,Layout layout);
 class Standard implements LeadingMarginSpan {
  private final int first,rest;
  public Standard(int first,int rest){this.first=first;this.rest=rest;}
  public Standard(int every){this(every,every);}
  public int getLeadingMargin(boolean isFirst){return isFirst?first:rest;}
  public void drawLeadingMargin(Canvas canvas,Paint paint,int x,int dir,int top,int baseline,int bottom,CharSequence text,int start,int end,boolean isFirst,Layout layout){}
 }
}
