// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;

import android.app.PendingIntent;
import android.os.Parcel;
import android.text.ParcelableSpan;

/** Span marking an easily editable/deletable text region with optional PendingIntent notification. */
public class EasyEditSpan implements ParcelableSpan {
    public static final String EXTRA_TEXT_CHANGED_TYPE =
            "android.text.style.EXTRA_TEXT_CHANGED_TYPE";
    public static final int TEXT_DELETED = 1;
    public static final int TEXT_MODIFIED = 2;

    private final PendingIntent mPendingIntent;
    private boolean mDeleteEnabled;

    public EasyEditSpan() {
        mPendingIntent = null;
        mDeleteEnabled = true;
    }

    public EasyEditSpan(PendingIntent pendingIntent) {
        mPendingIntent = pendingIntent;
        mDeleteEnabled = true;
    }

    public EasyEditSpan(Parcel source) {
        mPendingIntent = source.readParcelable(null);
        mDeleteEnabled = source.readByte() == 1;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mPendingIntent, 0);
        dest.writeByte((byte) (mDeleteEnabled ? 1 : 0));
    }

    @Override
    public void writeToParcelInternal(Parcel dest, int flags) {
        writeToParcel(dest, flags);
    }

    @Override
    public int getSpanTypeId() {
        return 22;
    }

    @Override
    public int getSpanTypeIdInternal() {
        return getSpanTypeId();
    }
}
