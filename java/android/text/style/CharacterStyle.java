// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.text.TextPaint;
public abstract class CharacterStyle {
 public abstract void updateDrawState(TextPaint paint);
 public CharacterStyle getUnderlying(){return this;}
 public static CharacterStyle wrap(final CharacterStyle style){if(style==null)throw new NullPointerException("style");if(style instanceof MetricAffectingSpan)return MetricAffectingSpan.wrapMetric((MetricAffectingSpan)style);return new CharacterStyle(){public void updateDrawState(TextPaint paint){style.updateDrawState(paint);}public CharacterStyle getUnderlying(){return style.getUnderlying();}};}
}
