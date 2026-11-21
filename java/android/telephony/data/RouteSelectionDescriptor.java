// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.data;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RouteSelectionDescriptor implements Parcelable {
    public static final int SESSION_TYPE_IPV4 = 0;
    public static final int SESSION_TYPE_IPV6 = 1;
    public static final int SESSION_TYPE_IPV4V6 = 2;

    public static final int ROUTE_SSC_MODE_1 = 1;
    public static final int ROUTE_SSC_MODE_2 = 2;
    public static final int ROUTE_SSC_MODE_3 = 3;

    private final int mPrecedence;
    private final int mSessionType;
    private final int mSscMode;
    private final List<NetworkSliceInfo> mSliceInfo;
    private final List<String> mDnn;

    RouteSelectionDescriptor(
            int precedence,
            int sessionType,
            int sscMode,
            List<NetworkSliceInfo> sliceInfo,
            List<String> dnn) {
        mPrecedence = precedence;
        mSessionType = sessionType;
        mSscMode = sscMode;
        mSliceInfo = sliceInfo != null
                ? Collections.unmodifiableList(new ArrayList<NetworkSliceInfo>(sliceInfo))
                : Collections.<NetworkSliceInfo>emptyList();
        mDnn = dnn != null
                ? Collections.unmodifiableList(new ArrayList<String>(dnn))
                : Collections.<String>emptyList();
    }

    private RouteSelectionDescriptor(Parcel p) {
        mPrecedence = p.readInt();
        mSessionType = p.readInt();
        mSscMode = p.readInt();
        List<NetworkSliceInfo> slices = p.createTypedArrayList(NetworkSliceInfo.CREATOR);
        mSliceInfo = slices != null ? Collections.unmodifiableList(slices) : Collections.<NetworkSliceInfo>emptyList();
        String[] dnns = p.createStringArray();
        if (dnns != null) {
            ArrayList<String> list = new ArrayList<String>(dnns.length);
            Collections.addAll(list, dnns);
            mDnn = Collections.unmodifiableList(list);
        } else {
            mDnn = Collections.emptyList();
        }
    }

    public int getPrecedence() {
        return mPrecedence;
    }

    public int getSessionType() {
        return mSessionType;
    }

    public int getSscMode() {
        return mSscMode;
    }

    public List<NetworkSliceInfo> getSliceInfo() {
        return mSliceInfo;
    }

    public List<String> getDataNetworkName() {
        return mDnn;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPrecedence);
        dest.writeInt(mSessionType);
        dest.writeInt(mSscMode);
        dest.writeTypedList(mSliceInfo);
        dest.writeStringArray(mDnn.toArray(new String[mDnn.size()]));
    }

    public int describeContents() {
        return 0;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RouteSelectionDescriptor)) return false;
        RouteSelectionDescriptor that = (RouteSelectionDescriptor) o;
        return mPrecedence == that.mPrecedence
                && mSessionType == that.mSessionType
                && mSscMode == that.mSscMode
                && Objects.equals(mSliceInfo, that.mSliceInfo)
                && Objects.equals(mDnn, that.mDnn);
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mPrecedence),
                Integer.valueOf(mSessionType),
                Integer.valueOf(mSscMode),
                mSliceInfo,
                mDnn);
    }

    public String toString() {
        return "{.precedence = " + mPrecedence
                + ", .sessionType = " + mSessionType
                + ", .sscMode = " + mSscMode
                + ", .sliceInfo = " + mSliceInfo
                + ", .dnn = " + mDnn + "}";
    }

    public static final Parcelable.Creator<RouteSelectionDescriptor> CREATOR =
            new Parcelable.Creator<RouteSelectionDescriptor>() {
                public RouteSelectionDescriptor createFromParcel(Parcel source) {
                    return new RouteSelectionDescriptor(source);
                }

                public RouteSelectionDescriptor[] newArray(int size) {
                    return new RouteSelectionDescriptor[size];
                }
            };
}
