// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
/** Inline content whose advance, metrics and rendering are supplied by the app. */
public abstract class ReplacementSpan extends MetricAffectingSpan {
 public abstract int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics);
 public abstract void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int baseline,int bottom,Paint paint);
 public void updateMeasureState(TextPaint paint){}
 public void updateDrawState(TextPaint paint){}
}
