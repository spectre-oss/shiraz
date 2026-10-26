// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.graphics.Typeface;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
/** Adds bold/italic style to the existing typeface for measurement and rendering. */
public class StyleSpan extends MetricAffectingSpan implements ParcelableSpan {
 private final int style;
 public StyleSpan(int style){this.style=style;}
 public StyleSpan(Parcel source){throw new UnsupportedOperationException("Parcel transport is not implemented");}
 public int getStyle(){return style;}
 public void updateMeasureState(TextPaint paint){
  Typeface previous=paint.getTypeface();int combined=style|(previous==null?0:previous.getStyle());
  Typeface selected=previous==null?Typeface.defaultFromStyle(combined):Typeface.create(previous,combined);
  int missing=combined&~selected.getStyle();if((missing&Typeface.BOLD)!=0)paint.setFakeBoldText(true);if((missing&Typeface.ITALIC)!=0)paint.setTextSkewX(-.25f);paint.setTypeface(selected);
 }
 public void updateDrawState(TextPaint paint){updateMeasureState(paint);}
 public int describeContents(){return 0;}public int getSpanTypeId(){return 7;}public int getSpanTypeIdInternal(){return getSpanTypeId();}
 public void writeToParcel(Parcel destination,int flags){throw new UnsupportedOperationException("Parcel transport is not implemented");}
 public void writeToParcelInternal(Parcel destination,int flags){writeToParcel(destination,flags);}
}
