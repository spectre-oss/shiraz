// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class GnssSignalType implements Parcelable {
    private final int mConstellationType;
    private final double mCarrierFrequencyHz;
    private final String mCodeType;

    private GnssSignalType(int constellationType, double carrierFrequencyHz, String codeType) {
        if (constellationType < 0) {
            throw new IllegalArgumentException("constellationType must be >= 0");
        }
        if (!(carrierFrequencyHz > 0.0) || Double.isInfinite(carrierFrequencyHz)) {
            throw new IllegalArgumentException("carrierFrequencyHz must be > 0");
        }
        mConstellationType = constellationType;
        mCarrierFrequencyHz = carrierFrequencyHz;
        mCodeType = Objects.requireNonNull(codeType, "codeType must not be null");
    }

    public static GnssSignalType create(
            int constellationType, double carrierFrequencyHz, String codeType) {
        return new GnssSignalType(constellationType, carrierFrequencyHz, codeType);
    }

    public int getConstellationType() {
        return mConstellationType;
    }

    public double getCarrierFrequencyHz() {
        return mCarrierFrequencyHz;
    }

    public String getCodeType() {
        return mCodeType;
    }

    public static final Parcelable.Creator<GnssSignalType> CREATOR =
            new Parcelable.Creator<GnssSignalType>() {
                @Override
                public GnssSignalType createFromParcel(Parcel parcel) {
                    return new GnssSignalType(
                            parcel.readInt(),
                            parcel.readDouble(),
                            parcel.readString());
                }

                @Override
                public GnssSignalType[] newArray(int i) {
                    return new GnssSignalType[i];
                }
            };

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mConstellationType);
        parcel.writeDouble(mCarrierFrequencyHz);
        parcel.writeString(mCodeType);
    }

    @Override
    public String toString() {
        return "GnssSignalType[Constellation=" + mConstellationType
                + ", CarrierFrequencyHz=" + mCarrierFrequencyHz
                + ", CodeType=" + mCodeType + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GnssSignalType)) {
            return false;
        }
        GnssSignalType s = (GnssSignalType) obj;
        return mConstellationType == s.mConstellationType
                && Double.compare(mCarrierFrequencyHz, s.mCarrierFrequencyHz) == 0
                && mCodeType.equals(s.mCodeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mConstellationType, mCarrierFrequencyHz, mCodeType);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
