// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class CellIdentityNr extends CellIdentity {
    private final int mNrArfcn;
    private final int mPci;
    private final int mTac;
    private final long mNci;
    private final int[] mBands;
    private final Set<String> mAdditionalPlmns;

    CellIdentityNr() {
        this(
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                new int[0],
                null,
                null,
                CellInfo.UNAVAILABLE_LONG,
                null,
                null,
                Collections.<String>emptySet());
    }

    CellIdentityNr(
            int pci,
            int tac,
            int nrArfcn,
            int[] bands,
            String mccStr,
            String mncStr,
            long nci,
            String alphal,
            String alphas,
            Set<String> additionalPlmns) {
        super(TYPE_NR, mccStr, mncStr, alphal, alphas);
        mPci = pci;
        mTac = tac;
        mNrArfcn = nrArfcn;
        mBands = bands != null ? bands.clone() : new int[0];
        Arrays.sort(mBands);
        mNci = nci;
        mAdditionalPlmns = additionalPlmns != null
                ? Collections.unmodifiableSet(new HashSet<String>(additionalPlmns))
                : Collections.<String>emptySet();
    }

    private CellIdentityNr(Parcel in) {
        super(TYPE_NR, in);
        mPci = in.readInt();
        mTac = in.readInt();
        mNrArfcn = in.readInt();
        int[] bands = in.createIntArray();
        mBands = bands != null ? bands : new int[0];
        mNci = in.readLong();
        String[] plmns = in.createStringArray();
        if (plmns != null) {
            HashSet<String> set = new HashSet<String>(plmns.length);
            Collections.addAll(set, plmns);
            mAdditionalPlmns = Collections.unmodifiableSet(set);
        } else {
            mAdditionalPlmns = Collections.emptySet();
        }
    }

    static CellIdentityNr createFromParcelBody(Parcel in) {
        return new CellIdentityNr(in);
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(super.hashCode()),
                Integer.valueOf(mPci),
                Integer.valueOf(mTac),
                Integer.valueOf(mNrArfcn),
                Integer.valueOf(Arrays.hashCode(mBands)),
                Long.valueOf(mNci),
                mAdditionalPlmns);
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellIdentityNr)) return false;
        CellIdentityNr o = (CellIdentityNr) other;
        return super.equals(o)
                && mPci == o.mPci
                && mTac == o.mTac
                && mNrArfcn == o.mNrArfcn
                && Arrays.equals(mBands, o.mBands)
                && mNci == o.mNci
                && Objects.equals(mAdditionalPlmns, o.mAdditionalPlmns);
    }

    public long getNci() {
        return mNci;
    }

    public int getNrarfcn() {
        return mNrArfcn;
    }

    public int[] getBands() {
        return mBands.clone();
    }

    public int getPci() {
        return mPci;
    }

    public int getTac() {
        return mTac;
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

    public String toString() {
        return "CellIdentityNr:{"
                + " mPci = " + mPci
                + " mTac = " + mTac
                + " mNrArfcn = " + mNrArfcn
                + " mBands = " + Arrays.toString(mBands)
                + " mMcc = " + mMccStr
                + " mMnc = " + mMncStr
                + " mNci = " + mNci
                + " mAlphaLong = " + mAlphaLong
                + " mAlphaShort = " + mAlphaShort
                + " mAdditionalPlmns = " + mAdditionalPlmns + " }";
    }

    public void writeToParcel(Parcel dest, int type) {
        super.writeToParcel(dest, TYPE_NR);
        dest.writeInt(mPci);
        dest.writeInt(mTac);
        dest.writeInt(mNrArfcn);
        dest.writeIntArray(mBands);
        dest.writeLong(mNci);
        dest.writeStringArray(mAdditionalPlmns.toArray(new String[mAdditionalPlmns.size()]));
    }

    public static final Parcelable.Creator<CellIdentityNr> CREATOR =
            new Parcelable.Creator<CellIdentityNr>() {
                public CellIdentityNr createFromParcel(Parcel in) {
                    in.readInt();
                    return createFromParcelBody(in);
                }

                public CellIdentityNr[] newArray(int size) {
                    return new CellIdentityNr[size];
                }
            };
}
