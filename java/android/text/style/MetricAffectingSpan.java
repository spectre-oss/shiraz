// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.text.TextPaint;
/** Base contract for spans which affect both measurement and drawing. */
public abstract class MetricAffectingSpan extends CharacterStyle implements UpdateLayout {
 public abstract void updateMeasureState(TextPaint paint);
 @Override public MetricAffectingSpan getUnderlying(){return this;}
 static MetricAffectingSpan wrapMetric(final MetricAffectingSpan style){return new MetricAffectingSpan(){
  public void updateDrawState(TextPaint paint){style.updateDrawState(paint);}
  public void updateMeasureState(TextPaint paint){style.updateMeasureState(paint);}
  public MetricAffectingSpan getUnderlying(){return style.getUnderlying();}
 };}
}
