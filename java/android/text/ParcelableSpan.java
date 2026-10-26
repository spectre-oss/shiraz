// SPDX-License-Identifier: GPL-3.0-only
package android.text;
import android.os.Parcel;
import android.os.Parcelable;
/** Framework span serialization contract. */
public interface ParcelableSpan extends Parcelable {
 int getSpanTypeId();
 int getSpanTypeIdInternal();
 void writeToParcelInternal(Parcel destination,int flags);
}
