// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class CellIdentityGsm extends CellIdentity {
    private final int mLac;
    private final int mCid;
    private final int mArfcn;
    private final int mBsic;
    private final Set<String> mAdditionalPlmns;

    CellIdentityGsm() {
        this(
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                null,
                null,
                null,
                null,
                Collections.<String>emptySet());
    }

    CellIdentityGsm(
            int lac,
            int cid,
            int arfcn,
            int bsic,
            String mccStr,
            String mncStr,
            String alphal,
            String alphas,
            Set<String> additionalPlmns) {
        super(TYPE_GSM, mccStr, mncStr, alphal, alphas);
        mLac = lac;
        mCid = cid;
        mArfcn = arfcn;
        mBsic = bsic;
        mAdditionalPlmns = additionalPlmns != null
                ? Collections.unmodifiableSet(new HashSet<String>(additionalPlmns))
                : Collections.<String>emptySet();
    }

    private CellIdentityGsm(Parcel in) {
        super(TYPE_GSM, in);
        mLac = in.readInt();
        mCid = in.readInt();
        mArfcn = in.readInt();
        mBsic = in.readInt();
        String[] plmns = in.createStringArray();
        if (plmns != null) {
            HashSet<String> set = new HashSet<String>(plmns.length);
            Collections.addAll(set, plmns);
            mAdditionalPlmns = Collections.unmodifiableSet(set);
        } else {
            mAdditionalPlmns = Collections.emptySet();
        }
    }

    static CellIdentityGsm createFromParcelBody(Parcel in) {
        return new CellIdentityGsm(in);
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

    public int getArfcn() {
        return mArfcn;
    }

    public int getBsic() {
        return mBsic;
    }

    public String getMobileNetworkOperator() {
        return (mMccStr == null || mMncStr == null) ? null : (mMccStr + mMncStr);
    }

    public String getMccString() {
        return mMccStr;
    }

    public String getMncString() {
        return mMncStr;
    }

    public Set<String> getAdditionalPlmns() {
        return mAdditionalPlmns;
    }

    @Deprecated
    public int getPsc() {
        return CellInfo.UNAVAILABLE;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mLac),
                Integer.valueOf(mCid),
                Integer.valueOf(mArfcn),
                Integer.valueOf(mBsic),
                mAdditionalPlmns,
                Integer.valueOf(super.hashCode()));
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellIdentityGsm)) return false;
        CellIdentityGsm o = (CellIdentityGsm) other;
        return mLac == o.mLac
                && mCid == o.mCid
                && mArfcn == o.mArfcn
                && mBsic == o.mBsic
                && Objects.equals(mAdditionalPlmns, o.mAdditionalPlmns)
                && super.equals(other);
    }

    public String toString() {
        return "CellIdentityGsm:{"
                + " mLac=" + mLac
                + " mCid=" + mCid
                + " mArfcn=" + mArfcn
                + " mBsic=0x" + Integer.toHexString(mBsic)
                + " mMcc=" + mMccStr
                + " mMnc=" + mMncStr
                + " mAlphaLong=" + mAlphaLong
                + " mAlphaShort=" + mAlphaShort
                + " mAdditionalPlmns=" + mAdditionalPlmns + "}";
    }

    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, TYPE_GSM);
        dest.writeInt(mLac);
        dest.writeInt(mCid);
        dest.writeInt(mArfcn);
        dest.writeInt(mBsic);
        dest.writeStringArray(mAdditionalPlmns.toArray(new String[mAdditionalPlmns.size()]));
    }

    public static final Parcelable.Creator<CellIdentityGsm> CREATOR =
            new Parcelable.Creator<CellIdentityGsm>() {
                public CellIdentityGsm createFromParcel(Parcel in) {
                    in.readInt(); // skip type
                    return createFromParcelBody(in);
                }

                public CellIdentityGsm[] newArray(int size) {
                    return new CellIdentityGsm[size];
                }
            };
}
