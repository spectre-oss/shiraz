// SPDX-License-Identifier: GPL-3.0-only
package android.service.carrier;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public class CarrierIdentifier implements Parcelable {
    public static final Parcelable.Creator<CarrierIdentifier> CREATOR =
            new Parcelable.Creator<CarrierIdentifier>() {
                @Override
                public CarrierIdentifier createFromParcel(Parcel in) {
                    return new CarrierIdentifier(
                            in.readString(),
                            in.readString(),
                            in.readString(),
                            in.readString(),
                            in.readString(),
                            in.readString(),
                            in.readInt(),
                            in.readInt());
                }

                @Override
                public CarrierIdentifier[] newArray(int size) {
                    return new CarrierIdentifier[size];
                }
            };

    private final String mMcc;
    private final String mMnc;
    private final String mSpn;
    private final String mImsi;
    private final String mGid1;
    private final String mGid2;
    private final int mCarrierId;
    private final int mSpecificCarrierId;

    public CarrierIdentifier(String mcc, String mnc, String spn, String imsi,
            String gid1, String gid2) {
        this(mcc, mnc, spn, imsi, gid1, gid2, -1, -1);
    }

    public CarrierIdentifier(String mcc, String mnc, String spn, String imsi,
            String gid1, String gid2, int carrierid, int specificCarrierId) {
        mMcc = mcc;
        mMnc = mnc;
        mSpn = spn;
        mImsi = imsi;
        mGid1 = gid1;
        mGid2 = gid2;
        mCarrierId = carrierid;
        mSpecificCarrierId = specificCarrierId;
    }

    public CarrierIdentifier(byte[] mccMnc, String gid1, String gid2) {
        Objects.requireNonNull(mccMnc, "mccMnc must not be null");
        if (mccMnc.length != 3) {
            throw new IllegalArgumentException("MCC & MNC must be set by a 3-byte array");
        }
        String hex = String.format("%02X%02X%02X",
                Integer.valueOf(mccMnc[0] & 0xFF),
                Integer.valueOf(mccMnc[1] & 0xFF),
                Integer.valueOf(mccMnc[2] & 0xFF));
        mMcc = "" + hex.charAt(1) + hex.charAt(0) + hex.charAt(3);
        if (hex.charAt(2) == 'F') {
            mMnc = "" + hex.charAt(5) + hex.charAt(4);
        } else {
            mMnc = "" + hex.charAt(5) + hex.charAt(4) + hex.charAt(2);
        }
        mSpn = null;
        mImsi = null;
        mGid1 = gid1;
        mGid2 = gid2;
        mCarrierId = -1;
        mSpecificCarrierId = -1;
    }

    public String getMcc() { return mMcc; }
    public String getMnc() { return mMnc; }
    public String getSpn() { return mSpn; }
    public String getImsi() { return mImsi; }
    public String getGid1() { return mGid1; }
    public String getGid2() { return mGid2; }
    public int getCarrierId() { return mCarrierId; }
    public int getSpecificCarrierId() { return mSpecificCarrierId; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CarrierIdentifier)) return false;
        CarrierIdentifier that = (CarrierIdentifier) obj;
        return mCarrierId == that.mCarrierId
                && mSpecificCarrierId == that.mSpecificCarrierId
                && Objects.equals(mMcc, that.mMcc)
                && Objects.equals(mMnc, that.mMnc)
                && Objects.equals(mSpn, that.mSpn)
                && Objects.equals(mImsi, that.mImsi)
                && Objects.equals(mGid1, that.mGid1)
                && Objects.equals(mGid2, that.mGid2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mMcc, mMnc, mSpn, mImsi, mGid1, mGid2, mCarrierId, mSpecificCarrierId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mMcc);
        out.writeString(mMnc);
        out.writeString(mSpn);
        out.writeString(mImsi);
        out.writeString(mGid1);
        out.writeString(mGid2);
        out.writeInt(mCarrierId);
        out.writeInt(mSpecificCarrierId);
    }

    @Override
    public String toString() {
        return "CarrierIdentifier{mcc=" + mMcc + ", mnc=" + mMnc + ", carrierId=" + mCarrierId + "}";
    }
}
