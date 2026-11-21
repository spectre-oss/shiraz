// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ClosedSubscriberGroupInfo implements Parcelable {
    private final boolean mCsgIndicator;
    private final String mHomeNodebName;
    private final int mCsgIdentity;

    ClosedSubscriberGroupInfo(boolean csgIndicator, String homeNodebName, int csgIdentity) {
        mCsgIndicator = csgIndicator;
        mHomeNodebName = homeNodebName != null ? homeNodebName : "";
        mCsgIdentity = csgIdentity;
    }

    private ClosedSubscriberGroupInfo(Parcel in) {
        mCsgIndicator = in.readBoolean();
        mHomeNodebName = in.readString();
        mCsgIdentity = in.readInt();
    }

    public boolean getCsgIndicator() {
        return mCsgIndicator;
    }

    public String getHomeNodebName() {
        return mHomeNodebName;
    }

    public int getCsgIdentity() {
        return mCsgIdentity;
    }

    public int hashCode() {
        return Objects.hash(
                Boolean.valueOf(mCsgIndicator),
                mHomeNodebName,
                Integer.valueOf(mCsgIdentity));
    }

    public boolean equals(Object other) {
        if (!(other instanceof ClosedSubscriberGroupInfo)) {
            return false;
        }
        ClosedSubscriberGroupInfo o = (ClosedSubscriberGroupInfo) other;
        return mCsgIndicator == o.mCsgIndicator
                && Objects.equals(mHomeNodebName, o.mHomeNodebName)
                && mCsgIdentity == o.mCsgIdentity;
    }

    public String toString() {
        return "ClosedSubscriberGroupInfo:{"
                + " mCsgIndicator = " + mCsgIndicator
                + " mHomeNodebName = " + mHomeNodebName
                + " mCsgIdentity = " + mCsgIdentity + "}";
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeBoolean(mCsgIndicator);
        dest.writeString(mHomeNodebName);
        dest.writeInt(mCsgIdentity);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<ClosedSubscriberGroupInfo> CREATOR =
            new Parcelable.Creator<ClosedSubscriberGroupInfo>() {
                public ClosedSubscriberGroupInfo createFromParcel(Parcel in) {
                    return new ClosedSubscriberGroupInfo(in);
                }

                public ClosedSubscriberGroupInfo[] newArray(int size) {
                    return new ClosedSubscriberGroupInfo[size];
                }
            };
}
