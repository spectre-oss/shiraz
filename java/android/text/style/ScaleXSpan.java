// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.text.TextPaint;
public class ScaleXSpan extends MetricAffectingSpan {
 private final float proportion;
 public ScaleXSpan(float proportion){this.proportion=proportion;}
 public float getScaleX(){return proportion;}
 public void updateMeasureState(TextPaint paint){paint.setTextScaleX(paint.getTextScaleX()*proportion);}
 public void updateDrawState(TextPaint paint){updateMeasureState(paint);}
}
