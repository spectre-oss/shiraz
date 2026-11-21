// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;
import java.util.Objects;

public final class BarringInfo implements Parcelable {
    public static final int BARRING_SERVICE_TYPE_CS_SERVICE = 0;
    public static final int BARRING_SERVICE_TYPE_PS_SERVICE = 1;
    public static final int BARRING_SERVICE_TYPE_CS_VOICE = 2;
    public static final int BARRING_SERVICE_TYPE_MO_SIGNALLING = 3;
    public static final int BARRING_SERVICE_TYPE_MO_DATA = 4;
    public static final int BARRING_SERVICE_TYPE_CS_FALLBACK = 5;
    public static final int BARRING_SERVICE_TYPE_MMTEL_VOICE = 6;
    public static final int BARRING_SERVICE_TYPE_MMTEL_VIDEO = 7;
    public static final int BARRING_SERVICE_TYPE_EMERGENCY = 8;
    public static final int BARRING_SERVICE_TYPE_SMS = 9;

    private static final BarringServiceInfo UNKNOWN_BARRING_INFO =
            new BarringServiceInfo(BarringServiceInfo.BARRING_TYPE_UNKNOWN, false, 0, 0);
    private static final BarringServiceInfo NONE_BARRING_INFO =
            new BarringServiceInfo(BarringServiceInfo.BARRING_TYPE_NONE, false, 0, 0);

    private final CellIdentity mCellIdentity;
    private final SparseArray<BarringServiceInfo> mBarringServiceInfos;

    BarringInfo() {
        mCellIdentity = null;
        mBarringServiceInfos = new SparseArray<BarringServiceInfo>();
    }

    BarringInfo(CellIdentity barringCellId, SparseArray<BarringServiceInfo> barringServiceInfos) {
        mCellIdentity = barringCellId;
        mBarringServiceInfos = barringServiceInfos != null
                ? barringServiceInfos : new SparseArray<BarringServiceInfo>();
    }

    private BarringInfo(Parcel in) {
        if (in.readInt() != 0) {
            mCellIdentity = CellIdentity.CREATOR.createFromParcel(in);
        } else {
            mCellIdentity = null;
        }
        int count = in.readInt();
        mBarringServiceInfos = new SparseArray<BarringServiceInfo>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            int key = in.readInt();
            BarringServiceInfo val = BarringServiceInfo.CREATOR.createFromParcel(in);
            mBarringServiceInfos.put(key, val);
        }
    }

    public BarringServiceInfo getBarringServiceInfo(int service) {
        BarringServiceInfo bsi = mBarringServiceInfos.get(service);
        if (bsi != null) {
            return bsi;
        }
        return mBarringServiceInfos.size() > 0 ? NONE_BARRING_INFO : UNKNOWN_BARRING_INFO;
    }

    public void writeToParcel(Parcel dest, int flags) {
        if (mCellIdentity != null) {
            dest.writeInt(1);
            mCellIdentity.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        int size = mBarringServiceInfos.size();
        dest.writeInt(size);
        for (int i = 0; i < size; i++) {
            dest.writeInt(mBarringServiceInfos.keyAt(i));
            mBarringServiceInfos.valueAt(i).writeToParcel(dest, flags);
        }
    }

    public int describeContents() {
        return 0;
    }

    public int hashCode() {
        int hash = mCellIdentity != null ? mCellIdentity.hashCode() : 7;
        for (int i = 0; i < mBarringServiceInfos.size(); i++) {
            hash += 15 * mBarringServiceInfos.keyAt(i);
            hash += 31 * mBarringServiceInfos.valueAt(i).hashCode();
        }
        return hash;
    }

    public boolean equals(Object rhs) {
        if (!(rhs instanceof BarringInfo)) {
            return false;
        }
        BarringInfo bi = (BarringInfo) rhs;
        if (!Objects.equals(mCellIdentity, bi.mCellIdentity)) {
            return false;
        }
        if (mBarringServiceInfos.size() != bi.mBarringServiceInfos.size()) {
            return false;
        }
        for (int i = 0; i < mBarringServiceInfos.size(); i++) {
            if (mBarringServiceInfos.keyAt(i) != bi.mBarringServiceInfos.keyAt(i)) {
                return false;
            }
            if (!Objects.equals(mBarringServiceInfos.valueAt(i),
                    bi.mBarringServiceInfos.valueAt(i))) {
                return false;
            }
        }
        return true;
    }

    public String toString() {
        return "BarringInfo {mCellIdentity=" + mCellIdentity
                + ", mBarringServiceInfos=" + mBarringServiceInfos + "}";
    }

    public static final Parcelable.Creator<BarringInfo> CREATOR =
            new Parcelable.Creator<BarringInfo>() {
                public BarringInfo createFromParcel(Parcel source) {
                    return new BarringInfo(source);
                }

                public BarringInfo[] newArray(int size) {
                    return new BarringInfo[size];
                }
            };

    public static final class BarringServiceInfo implements Parcelable {
        public static final int BARRING_TYPE_NONE = 0;
        public static final int BARRING_TYPE_CONDITIONAL = 1;
        public static final int BARRING_TYPE_UNCONDITIONAL = 2;
        public static final int BARRING_TYPE_UNKNOWN = -1;

        private final int mBarringType;
        private final boolean mIsConditionallyBarred;
        private final int mConditionalBarringFactor;
        private final int mConditionalBarringTimeSeconds;

        BarringServiceInfo(int barringType, boolean isConditionallyBarred,
                int conditionalBarringFactor, int conditionalBarringTimeSeconds) {
            mBarringType = barringType;
            mIsConditionallyBarred = isConditionallyBarred;
            mConditionalBarringFactor = conditionalBarringFactor;
            mConditionalBarringTimeSeconds = conditionalBarringTimeSeconds;
        }

        private BarringServiceInfo(Parcel in) {
            mBarringType = in.readInt();
            mIsConditionallyBarred = in.readInt() != 0;
            mConditionalBarringFactor = in.readInt();
            mConditionalBarringTimeSeconds = in.readInt();
        }

        public int getBarringType() {
            return mBarringType;
        }

        public boolean isConditionallyBarred() {
            return mIsConditionallyBarred;
        }

        public int getConditionalBarringFactor() {
            return mConditionalBarringFactor;
        }

        public int getConditionalBarringTimeSeconds() {
            return mConditionalBarringTimeSeconds;
        }

        public boolean isBarred() {
            return mBarringType == BARRING_TYPE_UNCONDITIONAL
                    || (mBarringType == BARRING_TYPE_CONDITIONAL && mIsConditionallyBarred);
        }

        public int hashCode() {
            return Objects.hash(
                    Integer.valueOf(mBarringType),
                    Boolean.valueOf(mIsConditionallyBarred),
                    Integer.valueOf(mConditionalBarringFactor),
                    Integer.valueOf(mConditionalBarringTimeSeconds));
        }

        public boolean equals(Object rhs) {
            if (!(rhs instanceof BarringServiceInfo)) {
                return false;
            }
            BarringServiceInfo other = (BarringServiceInfo) rhs;
            return mBarringType == other.mBarringType
                    && mIsConditionallyBarred == other.mIsConditionallyBarred
                    && mConditionalBarringFactor == other.mConditionalBarringFactor
                    && mConditionalBarringTimeSeconds == other.mConditionalBarringTimeSeconds;
        }

        public String toString() {
            return "BarringServiceInfo {mBarringType=" + mBarringType
                    + ", mIsConditionallyBarred=" + mIsConditionallyBarred
                    + ", mConditionalBarringFactor=" + mConditionalBarringFactor
                    + ", mConditionalBarringTimeSeconds=" + mConditionalBarringTimeSeconds + "}";
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mBarringType);
            dest.writeInt(mIsConditionallyBarred ? 1 : 0);
            dest.writeInt(mConditionalBarringFactor);
            dest.writeInt(mConditionalBarringTimeSeconds);
        }

        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<BarringServiceInfo> CREATOR =
                new Parcelable.Creator<BarringServiceInfo>() {
                    public BarringServiceInfo createFromParcel(Parcel source) {
                        return new BarringServiceInfo(source);
                    }

                    public BarringServiceInfo[] newArray(int size) {
                        return new BarringServiceInfo[size];
                    }
                };
    }
}
