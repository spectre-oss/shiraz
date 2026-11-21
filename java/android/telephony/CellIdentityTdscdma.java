// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class CellIdentityTdscdma extends CellIdentity {
    private final int mLac;
    private final int mCid;
    private final int mCpid;
    private final int mUarfcn;
    private final Set<String> mAdditionalPlmns;
    private final ClosedSubscriberGroupInfo mCsgInfo;

    CellIdentityTdscdma() {
        this(
                null,
                null,
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                null,
                null,
                Collections.<String>emptySet(),
                null);
    }

    CellIdentityTdscdma(
            String mcc,
            String mnc,
            int lac,
            int cid,
            int cpid,
            int uarfcn,
            String alphal,
            String alphas,
            Set<String> additionalPlmns,
            ClosedSubscriberGroupInfo csgInfo) {
        super(TYPE_TDSCDMA, mcc, mnc, alphal, alphas);
        mLac = lac;
        mCid = cid;
        mCpid = cpid;
        mUarfcn = uarfcn;
        mAdditionalPlmns = additionalPlmns != null
                ? Collections.unmodifiableSet(new HashSet<String>(additionalPlmns))
                : Collections.<String>emptySet();
        mCsgInfo = csgInfo;
    }

    private CellIdentityTdscdma(Parcel in) {
        super(TYPE_TDSCDMA, in);
        mLac = in.readInt();
        mCid = in.readInt();
        mCpid = in.readInt();
        mUarfcn = in.readInt();
        String[] plmns = in.createStringArray();
        if (plmns != null) {
            HashSet<String> set = new HashSet<String>(plmns.length);
            Collections.addAll(set, plmns);
            mAdditionalPlmns = Collections.unmodifiableSet(set);
        } else {
            mAdditionalPlmns = Collections.emptySet();
        }
        mCsgInfo = in.readTypedObject(ClosedSubscriberGroupInfo.CREATOR);
    }

    static CellIdentityTdscdma createFromParcelBody(Parcel in) {
        return new CellIdentityTdscdma(in);
    }

    public String getMccString() {
        return mMccStr;
    }

    public String getMncString() {
        return mMncStr;
    }

    public String getMobileNetworkOperator() {
        return (mMccStr == null || mMncStr == null) ? null : (mMccStr + mMncStr);
    }

    public int getLac() {
        return mLac;
    }

    public int getCid() {
        return mCid;
    }

    public int getCpid() {
        return mCpid;
    }

    public int getUarfcn() {
        return mUarfcn;
    }

    public Set<String> getAdditionalPlmns() {
        return mAdditionalPlmns;
    }

    public ClosedSubscriberGroupInfo getClosedSubscriberGroupInfo() {
        return mCsgInfo;
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellIdentityTdscdma)) return false;
        CellIdentityTdscdma o = (CellIdentityTdscdma) other;
        return mLac == o.mLac
                && mCid == o.mCid
                && mCpid == o.mCpid
                && mUarfcn == o.mUarfcn
                && Objects.equals(mAdditionalPlmns, o.mAdditionalPlmns)
                && Objects.equals(mCsgInfo, o.mCsgInfo)
                && super.equals(other);
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mLac),
                Integer.valueOf(mCid),
                Integer.valueOf(mCpid),
                Integer.valueOf(mUarfcn),
                mAdditionalPlmns,
                mCsgInfo,
                Integer.valueOf(super.hashCode()));
    }

    public String toString() {
        return "CellIdentityTdscdma:{"
                + " mMcc=" + mMccStr
                + " mMnc=" + mMncStr
                + " mAlphaLong=" + mAlphaLong
                + " mAlphaShort=" + mAlphaShort
                + " mLac=" + mLac
                + " mCid=" + mCid
                + " mCpid=" + mCpid
                + " mUarfcn=" + mUarfcn
                + " mAdditionalPlmns=" + mAdditionalPlmns
                + " mCsgInfo=" + mCsgInfo + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, TYPE_TDSCDMA);
        dest.writeInt(mLac);
        dest.writeInt(mCid);
        dest.writeInt(mCpid);
        dest.writeInt(mUarfcn);
        dest.writeStringArray(mAdditionalPlmns.toArray(new String[mAdditionalPlmns.size()]));
        dest.writeTypedObject(mCsgInfo, flags);
    }

    public static final Parcelable.Creator<CellIdentityTdscdma> CREATOR =
            new Parcelable.Creator<CellIdentityTdscdma>() {
                public CellIdentityTdscdma createFromParcel(Parcel in) {
                    in.readInt();
                    return createFromParcelBody(in);
                }

                public CellIdentityTdscdma[] newArray(int size) {
                    return new CellIdentityTdscdma[size];
                }
            };
}
