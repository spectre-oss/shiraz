// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.text.TextPaint;
public class SuperscriptSpan extends MetricAffectingSpan {
 public void updateMeasureState(TextPaint paint){paint.baselineShift+=(int)(paint.ascent()/2);}
 public void updateDrawState(TextPaint paint){updateMeasureState(paint);}
}
