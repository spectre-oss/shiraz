// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public abstract class CellIdentity implements Parcelable {
    static final int TYPE_GSM = 1;
    static final int TYPE_CDMA = 2;
    static final int TYPE_LTE = 3;
    static final int TYPE_WCDMA = 4;
    static final int TYPE_TDSCDMA = 5;
    static final int TYPE_NR = 6;

    final int mType;
    final String mMccStr;
    final String mMncStr;
    final String mAlphaLong;
    final String mAlphaShort;

    CellIdentity(int type, String mcc, String mnc, String alphal, String alphas) {
        mType = type;
        mMccStr = (mcc == null || isValidPlmnPart(mcc, 3, 3)) ? mcc : null;
        mMncStr = (mnc == null || isValidPlmnPart(mnc, 2, 3)) ? mnc : null;
        mAlphaLong = alphal;
        mAlphaShort = alphas;
    }

    CellIdentity(int type, Parcel in) {
        mType = type;
        mMccStr = in.readString();
        mMncStr = in.readString();
        mAlphaLong = in.readString();
        mAlphaShort = in.readString();
    }

    private static boolean isValidPlmnPart(String s, int minLen, int maxLen) {
        if (s.length() < minLen || s.length() > maxLen) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    public int describeContents() {
        return 0;
    }

    public CharSequence getOperatorAlphaLong() {
        return mAlphaLong;
    }

    public CharSequence getOperatorAlphaShort() {
        return mAlphaShort;
    }

    public boolean equals(Object other) {
        if (!(other instanceof CellIdentity)) {
            return false;
        }
        CellIdentity o = (CellIdentity) other;
        return mType == o.mType
                && Objects.equals(mMccStr, o.mMccStr)
                && Objects.equals(mMncStr, o.mMncStr)
                && Objects.equals(mAlphaLong, o.mAlphaLong)
                && Objects.equals(mAlphaShort, o.mAlphaShort);
    }

    public int hashCode() {
        return Objects.hash(mAlphaLong, mAlphaShort, mMccStr, mMncStr, Integer.valueOf(mType));
    }

    public void writeToParcel(Parcel dest, int type) {
        dest.writeInt(type);
        dest.writeString(mMccStr);
        dest.writeString(mMncStr);
        dest.writeString(mAlphaLong);
        dest.writeString(mAlphaShort);
    }

    public static final Parcelable.Creator<CellIdentity> CREATOR =
            new Parcelable.Creator<CellIdentity>() {
                public CellIdentity createFromParcel(Parcel in) {
                    int type = in.readInt();
                    switch (type) {
                        case TYPE_GSM:
                            return CellIdentityGsm.createFromParcelBody(in);
                        case TYPE_WCDMA:
                            return CellIdentityWcdma.createFromParcelBody(in);
                        case TYPE_CDMA:
                            return CellIdentityCdma.createFromParcelBody(in);
                        case TYPE_LTE:
                            return CellIdentityLte.createFromParcelBody(in);
                        case TYPE_TDSCDMA:
                            return CellIdentityTdscdma.createFromParcelBody(in);
                        case TYPE_NR:
                            return CellIdentityNr.createFromParcelBody(in);
                        default:
                            throw new IllegalArgumentException("Bad Cell identity Parcel");
                    }
                }

                public CellIdentity[] newArray(int size) {
                    return new CellIdentity[size];
                }
            };
}
