// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.text.TextPaint;
public class RelativeSizeSpan extends MetricAffectingSpan {
 private final float proportion;
 public RelativeSizeSpan(float proportion){this.proportion=proportion;}
 public float getSizeChange(){return proportion;}
 public void updateMeasureState(TextPaint paint){paint.setTextSize(paint.getTextSize()*proportion);}
 public void updateDrawState(TextPaint paint){updateMeasureState(paint);}
}
