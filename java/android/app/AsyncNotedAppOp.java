// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class AsyncNotedAppOp implements Parcelable {
    private final String mOp;
    private final int mNotingUid;
    private final String mAttributionTag;
    private final String mMessage;
    private final long mTime;

    AsyncNotedAppOp(String op, int notingUid, String attributionTag, String message, long time) {
        mOp = Objects.requireNonNull(op, "op");
        mNotingUid = notingUid;
        mAttributionTag = attributionTag;
        mMessage = Objects.requireNonNull(message, "message");
        mTime = time;
    }

    private AsyncNotedAppOp(Parcel in) {
        mOp = in.readString();
        mNotingUid = in.readInt();
        mAttributionTag = in.readString();
        mMessage = in.readString();
        mTime = in.readLong();
    }

    public String getOp() {
        return mOp;
    }

    public int getNotingUid() {
        return mNotingUid;
    }

    public String getAttributionTag() {
        return mAttributionTag;
    }

    public String getMessage() {
        return mMessage;
    }

    public long getTime() {
        return mTime;
    }

    @Override
    public String toString() {
        return "AsyncNotedAppOp(op=" + mOp + ", notingUid=" + mNotingUid
                + ", attributionTag=" + mAttributionTag + ", message=" + mMessage
                + ", time=" + mTime + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AsyncNotedAppOp)) {
            return false;
        }
        AsyncNotedAppOp that = (AsyncNotedAppOp) o;
        return mNotingUid == that.mNotingUid
                && mTime == that.mTime
                && Objects.equals(mOp, that.mOp)
                && Objects.equals(mAttributionTag, that.mAttributionTag)
                && Objects.equals(mMessage, that.mMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mOp, Integer.valueOf(mNotingUid), mAttributionTag, mMessage, Long.valueOf(mTime));
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mOp);
        dest.writeInt(mNotingUid);
        dest.writeString(mAttributionTag);
        dest.writeString(mMessage);
        dest.writeLong(mTime);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<AsyncNotedAppOp> CREATOR =
            new Parcelable.Creator<AsyncNotedAppOp>() {
                @Override
                public AsyncNotedAppOp createFromParcel(Parcel in) {
                    return new AsyncNotedAppOp(in);
                }

                @Override
                public AsyncNotedAppOp[] newArray(int size) {
                    return new AsyncNotedAppOp[size];
                }
            };
}
