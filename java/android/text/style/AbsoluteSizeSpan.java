// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
/** Sets a run's text size in physical pixels or density-independent pixels. */
public class AbsoluteSizeSpan extends MetricAffectingSpan implements ParcelableSpan {
 private final int size;
 private final boolean dip;
 public AbsoluteSizeSpan(int size){this(size,false);}
 public AbsoluteSizeSpan(int size,boolean dip){this.size=size;this.dip=dip;}
 public AbsoluteSizeSpan(Parcel source){throw new UnsupportedOperationException("Parcel transport is not implemented");}
 public int getSize(){return size;}
 public boolean getDip(){return dip;}
 public void updateMeasureState(TextPaint paint){paint.setTextSize(dip?size*paint.density:size);}
 public void updateDrawState(TextPaint paint){updateMeasureState(paint);}
 public int describeContents(){return 0;}
 public int getSpanTypeId(){return 16;}
 public int getSpanTypeIdInternal(){return getSpanTypeId();}
 public void writeToParcel(Parcel destination,int flags){throw new UnsupportedOperationException("Parcel transport is not implemented");}
 public void writeToParcelInternal(Parcel destination,int flags){writeToParcel(destination,flags);}
}
