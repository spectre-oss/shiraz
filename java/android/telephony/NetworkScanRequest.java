// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public final class NetworkScanRequest implements Parcelable {
    public static final int SCAN_TYPE_ONE_SHOT = 0;
    public static final int SCAN_TYPE_PERIODIC = 1;

    private final int mScanType;
    private final RadioAccessSpecifier[] mSpecifiers;
    private final int mSearchPeriodicity;
    private final int mMaxSearchTime;
    private final boolean mIncrementalResults;
    private final int mIncrementalResultsPeriodicity;
    private final ArrayList<String> mMccMncs;

    public NetworkScanRequest(int scanType, RadioAccessSpecifier[] specifiers,
            int searchPeriodicity, int maxSearchTime, boolean incrementalResults,
            int incrementalResultsPeriodicity, ArrayList<String> mccMncs) {
        mScanType = scanType;
        mSpecifiers = specifiers != null ? specifiers.clone() : null;
        mSearchPeriodicity = searchPeriodicity;
        mMaxSearchTime = maxSearchTime;
        mIncrementalResults = incrementalResults;
        mIncrementalResultsPeriodicity = incrementalResultsPeriodicity;
        mMccMncs = mccMncs != null ? new ArrayList<String>(mccMncs) : new ArrayList<String>();
    }

    private NetworkScanRequest(Parcel in) {
        mScanType = in.readInt();
        mSpecifiers = in.createTypedArray(RadioAccessSpecifier.CREATOR);
        mSearchPeriodicity = in.readInt();
        mMaxSearchTime = in.readInt();
        mIncrementalResults = in.readInt() != 0;
        mIncrementalResultsPeriodicity = in.readInt();
        int count = in.readInt();
        mMccMncs = new ArrayList<String>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            mMccMncs.add(in.readString());
        }
    }

    public int getScanType() {
        return mScanType;
    }

    public int getSearchPeriodicity() {
        return mSearchPeriodicity;
    }

    public int getMaxSearchTime() {
        return mMaxSearchTime;
    }

    public boolean getIncrementalResults() {
        return mIncrementalResults;
    }

    public int getIncrementalResultsPeriodicity() {
        return mIncrementalResultsPeriodicity;
    }

    public RadioAccessSpecifier[] getSpecifiers() {
        return mSpecifiers != null ? mSpecifiers.clone() : null;
    }

    public ArrayList<String> getPlmns() {
        return new ArrayList<String>(mMccMncs);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mScanType);
        dest.writeTypedArray(mSpecifiers, flags);
        dest.writeInt(mSearchPeriodicity);
        dest.writeInt(mMaxSearchTime);
        dest.writeInt(mIncrementalResults ? 1 : 0);
        dest.writeInt(mIncrementalResultsPeriodicity);
        dest.writeInt(mMccMncs.size());
        for (int i = 0; i < mMccMncs.size(); i++) {
            dest.writeString(mMccMncs.get(i));
        }
    }

    public boolean equals(Object o) {
        if (!(o instanceof NetworkScanRequest)) {
            return false;
        }
        NetworkScanRequest nsr = (NetworkScanRequest) o;
        return mScanType == nsr.mScanType
                && Arrays.equals(mSpecifiers, nsr.mSpecifiers)
                && mSearchPeriodicity == nsr.mSearchPeriodicity
                && mMaxSearchTime == nsr.mMaxSearchTime
                && mIncrementalResults == nsr.mIncrementalResults
                && mIncrementalResultsPeriodicity == nsr.mIncrementalResultsPeriodicity
                && Objects.equals(mMccMncs, nsr.mMccMncs);
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mScanType),
                Integer.valueOf(Arrays.hashCode(mSpecifiers)),
                Integer.valueOf(mSearchPeriodicity),
                Integer.valueOf(mMaxSearchTime),
                Boolean.valueOf(mIncrementalResults),
                Integer.valueOf(mIncrementalResultsPeriodicity),
                mMccMncs);
    }

    public static final Parcelable.Creator<NetworkScanRequest> CREATOR =
            new Parcelable.Creator<NetworkScanRequest>() {
                public NetworkScanRequest createFromParcel(Parcel in) {
                    return new NetworkScanRequest(in);
                }

                public NetworkScanRequest[] newArray(int size) {
                    return new NetworkScanRequest[size];
                }
            };
}
