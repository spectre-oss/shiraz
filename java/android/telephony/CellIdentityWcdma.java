// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class CellIdentityWcdma extends CellIdentity {
    private final int mLac;
    private final int mCid;
    private final int mPsc;
    private final int mUarfcn;
    private final Set<String> mAdditionalPlmns;
    private final ClosedSubscriberGroupInfo mCsgInfo;

    CellIdentityWcdma() {
        this(
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                null,
                null,
                null,
                null,
                Collections.<String>emptySet(),
                null);
    }

    CellIdentityWcdma(
            int lac,
            int cid,
            int psc,
            int uarfcn,
            String mccStr,
            String mncStr,
            String alphal,
            String alphas,
            Set<String> additionalPlmns,
            ClosedSubscriberGroupInfo csgInfo) {
        super(TYPE_WCDMA, mccStr, mncStr, alphal, alphas);
        mLac = lac;
        mCid = cid;
        mPsc = psc;
        mUarfcn = uarfcn;
        mAdditionalPlmns = additionalPlmns != null
                ? Collections.unmodifiableSet(new HashSet<String>(additionalPlmns))
                : Collections.<String>emptySet();
        mCsgInfo = csgInfo;
    }

    private CellIdentityWcdma(Parcel in) {
        super(TYPE_WCDMA, in);
        mLac = in.readInt();
        mCid = in.readInt();
        mPsc = in.readInt();
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

    static CellIdentityWcdma createFromParcelBody(Parcel in) {
        return new CellIdentityWcdma(in);
    }

    @Deprecated
    public int getMcc() {
        return mMccStr != null ? Integer.parseInt(mMccStr) : CellInfo.UNAVAILABLE;
    }

    @Deprecated
    public int getMnc() {
        return mMncStr != null ? Integer.parseInt(mMncStr) : CellInfo.UNAVAILABLE;
    }

    public int getLac() {
        return mLac;
    }

    public int getCid() {
        return mCid;
    }

    public int getPsc() {
        return mPsc;
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

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mLac),
                Integer.valueOf(mCid),
                Integer.valueOf(mPsc),
                Integer.valueOf(mUarfcn),
                mAdditionalPlmns,
                mCsgInfo,
                Integer.valueOf(super.hashCode()));
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
        if (!(other instanceof CellIdentityWcdma)) return false;
        CellIdentityWcdma o = (CellIdentityWcdma) other;
        return mLac == o.mLac
                && mCid == o.mCid
                && mPsc == o.mPsc
                && mUarfcn == o.mUarfcn
                && Objects.equals(mAdditionalPlmns, o.mAdditionalPlmns)
                && Objects.equals(mCsgInfo, o.mCsgInfo)
                && super.equals(other);
    }

    public String toString() {
        return "CellIdentityWcdma:{"
                + " mLac=" + mLac
                + " mCid=" + mCid
                + " mPsc=" + mPsc
                + " mUarfcn=" + mUarfcn
                + " mMcc=" + mMccStr
                + " mMnc=" + mMncStr
                + " mAlphaLong=" + mAlphaLong
                + " mAlphaShort=" + mAlphaShort
                + " mAdditionalPlmns=" + mAdditionalPlmns
                + " mCsgInfo=" + mCsgInfo + "}";
    }

    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, TYPE_WCDMA);
        dest.writeInt(mLac);
        dest.writeInt(mCid);
        dest.writeInt(mPsc);
        dest.writeInt(mUarfcn);
        dest.writeStringArray(mAdditionalPlmns.toArray(new String[mAdditionalPlmns.size()]));
        dest.writeTypedObject(mCsgInfo, flags);
    }

    public static final Parcelable.Creator<CellIdentityWcdma> CREATOR =
            new Parcelable.Creator<CellIdentityWcdma>() {
                public CellIdentityWcdma createFromParcel(Parcel in) {
                    in.readInt();
                    return createFromParcelBody(in);
                }

                public CellIdentityWcdma[] newArray(int size) {
                    return new CellIdentityWcdma[size];
                }
            };
}
