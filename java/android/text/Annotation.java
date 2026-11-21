// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import android.os.Parcel;

/** Key-value metadata span attached to styled text ranges. */
public class Annotation implements ParcelableSpan {
    private final String mKey;
    private final String mValue;

    public Annotation(String key, String value) {
        mKey = key;
        mValue = value;
    }

    public Annotation(Parcel src) {
        mKey = src.readString();
        mValue = src.readString();
    }

    @Override
    public int getSpanTypeId() {
        return 18;
    }

    @Override
    public int getSpanTypeIdInternal() {
        return getSpanTypeId();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mKey);
        dest.writeString(mValue);
    }

    @Override
    public void writeToParcelInternal(Parcel dest, int flags) {
        writeToParcel(dest, flags);
    }

    public String getKey() {
        return mKey;
    }

    public String getValue() {
        return mValue;
    }
}
