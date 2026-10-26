// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.graphics.Typeface;
import android.text.TextPaint;
public class TypefaceSpan extends MetricAffectingSpan {
 private final String family;
 private final Typeface typeface;
 public TypefaceSpan(String family){this.family=family;typeface=null;}
 public TypefaceSpan(Typeface typeface){this.typeface=typeface;family=null;}
 public String getFamily(){return family;}
 public Typeface getTypeface(){return typeface;}
 private void apply(TextPaint paint){
  Typeface old=paint.getTypeface();int style=old==null?Typeface.NORMAL:old.getStyle();
  paint.setTypeface(typeface!=null?Typeface.create(typeface,style):Typeface.create(family,style));
 }
 public void updateMeasureState(TextPaint paint){apply(paint);}
 public void updateDrawState(TextPaint paint){apply(paint);}
}
