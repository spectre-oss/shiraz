// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class CellIdentityLte extends CellIdentity {
    private final int mCi;
    private final int mPci;
    private final int mTac;
    private final int mEarfcn;
    private final int[] mBands;
    private final int mBandwidth;
    private final Set<String> mAdditionalPlmns;
    private final ClosedSubscriberGroupInfo mCsgInfo;

    CellIdentityLte() {
        this(
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                new int[0],
                CellInfo.UNAVAILABLE,
                null,
                null,
                null,
                null,
                Collections.<String>emptySet(),
                null);
    }

    CellIdentityLte(
            int ci,
            int pci,
            int tac,
            int earfcn,
            int[] bands,
            int bandwidth,
            String mccStr,
            String mncStr,
            String alphal,
            String alphas,
            Set<String> additionalPlmns,
            ClosedSubscriberGroupInfo csgInfo) {
        super(TYPE_LTE, mccStr, mncStr, alphal, alphas);
        mCi = ci;
        mPci = pci;
        mTac = tac;
        mEarfcn = earfcn;
        mBands = bands != null ? bands.clone() : new int[0];
        Arrays.sort(mBands);
        mBandwidth = bandwidth;
        mAdditionalPlmns = additionalPlmns != null
                ? Collections.unmodifiableSet(new HashSet<String>(additionalPlmns))
                : Collections.<String>emptySet();
        mCsgInfo = csgInfo;
    }

    private CellIdentityLte(Parcel in) {
        super(TYPE_LTE, in);
        mCi = in.readInt();
        mPci = in.readInt();
        mTac = in.readInt();
        mEarfcn = in.readInt();
        int[] bands = in.createIntArray();
        mBands = bands != null ? bands : new int[0];
        mBandwidth = in.readInt();
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

    static CellIdentityLte createFromParcelBody(Parcel in) {
        return new CellIdentityLte(in);
    }

    @Deprecated
    public int getMcc() {
        return mMccStr != null ? Integer.parseInt(mMccStr) : CellInfo.UNAVAILABLE;
    }

    @Deprecated
    public int getMnc() {
        return mMncStr != null ? Integer.parseInt(mMncStr) : CellInfo.UNAVAILABLE;
    }

    public int getCi() {
        return mCi;
    }

    public int getPci() {
        return mPci;
    }

    public int getTac() {
        return mTac;
    }

    public int getEarfcn() {
        return mEarfcn;
    }

    public int[] getBands() {
        return mBands.clone();
    }

    public int getBandwidth() {
        return mBandwidth;
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

    public Set<String> getAdditionalPlmns() {
        return mAdditionalPlmns;
    }

    public ClosedSubscriberGroupInfo getClosedSubscriberGroupInfo() {
        return mCsgInfo;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mCi),
                Integer.valueOf(mPci),
                Integer.valueOf(mTac),
                Integer.valueOf(mEarfcn),
                Integer.valueOf(Arrays.hashCode(mBands)),
                Integer.valueOf(mBandwidth),
                mAdditionalPlmns,
                mCsgInfo,
                Integer.valueOf(super.hashCode()));
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellIdentityLte)) return false;
        CellIdentityLte o = (CellIdentityLte) other;
        return mCi == o.mCi
                && mPci == o.mPci
                && mTac == o.mTac
                && mEarfcn == o.mEarfcn
                && Arrays.equals(mBands, o.mBands)
                && mBandwidth == o.mBandwidth
                && Objects.equals(mAdditionalPlmns, o.mAdditionalPlmns)
                && Objects.equals(mCsgInfo, o.mCsgInfo)
                && super.equals(other);
    }

    public String toString() {
        return "CellIdentityLte:{"
                + " mCi=" + mCi
                + " mPci=" + mPci
                + " mTac=" + mTac
                + " mEarfcn=" + mEarfcn
                + " mBands=" + Arrays.toString(mBands)
                + " mBandwidth=" + mBandwidth
                + " mMcc=" + mMccStr
                + " mMnc=" + mMncStr
                + " mAlphaLong=" + mAlphaLong
                + " mAlphaShort=" + mAlphaShort
                + " mAdditionalPlmns=" + mAdditionalPlmns
                + " mCsgInfo=" + mCsgInfo + "}";
    }

    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, TYPE_LTE);
        dest.writeInt(mCi);
        dest.writeInt(mPci);
        dest.writeInt(mTac);
        dest.writeInt(mEarfcn);
        dest.writeIntArray(mBands);
        dest.writeInt(mBandwidth);
        dest.writeStringArray(mAdditionalPlmns.toArray(new String[mAdditionalPlmns.size()]));
        dest.writeTypedObject(mCsgInfo, flags);
    }

    public static final Parcelable.Creator<CellIdentityLte> CREATOR =
            new Parcelable.Creator<CellIdentityLte>() {
                public CellIdentityLte createFromParcel(Parcel in) {
                    in.readInt();
                    return createFromParcelBody(in);
                }

                public CellIdentityLte[] newArray(int size) {
                    return new CellIdentityLte[size];
                }
            };
}
