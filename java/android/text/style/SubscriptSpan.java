// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
/** Moves text below the baseline without changing its font size. */
public class SubscriptSpan extends MetricAffectingSpan implements ParcelableSpan {
 public SubscriptSpan(){}
 public SubscriptSpan(Parcel source){}
 public void updateMeasureState(TextPaint paint){paint.baselineShift-=(int)(paint.ascent()/2);}
 public void updateDrawState(TextPaint paint){updateMeasureState(paint);}
 public int describeContents(){return 0;}
 public int getSpanTypeId(){return 15;}
 public int getSpanTypeIdInternal(){return getSpanTypeId();}
 public void writeToParcel(Parcel destination,int flags){}
 public void writeToParcelInternal(Parcel destination,int flags){writeToParcel(destination,flags);}
}
