// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class GnssAutomaticGainControl implements Parcelable {
    private final double mLevelDb;
    private final int mConstellationType;
    private final long mCarrierFrequencyHz;

    private GnssAutomaticGainControl(
            double levelDb, int constellationType, long carrierFrequencyHz) {
        mLevelDb = levelDb;
        mConstellationType = constellationType;
        mCarrierFrequencyHz = carrierFrequencyHz;
    }

    public double getLevelDb() {
        return mLevelDb;
    }

    public int getConstellationType() {
        return mConstellationType;
    }

    public long getCarrierFrequencyHz() {
        return mCarrierFrequencyHz;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flag) {
        parcel.writeDouble(mLevelDb);
        parcel.writeInt(mConstellationType);
        parcel.writeLong(mCarrierFrequencyHz);
    }

    public static final Parcelable.Creator<GnssAutomaticGainControl> CREATOR =
            new Parcelable.Creator<GnssAutomaticGainControl>() {
                @Override
                public GnssAutomaticGainControl createFromParcel(Parcel parcel) {
                    return new GnssAutomaticGainControl(
                            parcel.readDouble(),
                            parcel.readInt(),
                            parcel.readLong());
                }

                @Override
                public GnssAutomaticGainControl[] newArray(int i) {
                    return new GnssAutomaticGainControl[i];
                }
            };

    @Override
    public String toString() {
        return "GnssAutomaticGainControl[LevelDb=" + mLevelDb
                + " Constellation=" + mConstellationType
                + " CarrierFrequencyHz=" + mCarrierFrequencyHz + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GnssAutomaticGainControl)) {
            return false;
        }
        GnssAutomaticGainControl other = (GnssAutomaticGainControl) obj;
        return Double.compare(mLevelDb, other.mLevelDb) == 0
                && mConstellationType == other.mConstellationType
                && mCarrierFrequencyHz == other.mCarrierFrequencyHz;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLevelDb, mConstellationType, mCarrierFrequencyHz);
    }

    public static final class Builder {
        private double mLevelDb;
        private int mConstellationType;
        private long mCarrierFrequencyHz;

        public Builder() {
        }

        public Builder(GnssAutomaticGainControl agc) {
            Objects.requireNonNull(agc, "agc must not be null");
            mLevelDb = agc.getLevelDb();
            mConstellationType = agc.getConstellationType();
            mCarrierFrequencyHz = agc.getCarrierFrequencyHz();
        }

        public Builder setLevelDb(double levelDb) {
            if (!Double.isFinite(levelDb) || levelDb < -10000.0 || levelDb > 10000.0) {
                throw new IllegalArgumentException("Invalid levelDb: " + levelDb);
            }
            mLevelDb = levelDb;
            return this;
        }

        public Builder setConstellationType(int constellationType) {
            if (constellationType < 0) {
                throw new IllegalArgumentException(
                        "Invalid constellationType: " + constellationType);
            }
            mConstellationType = constellationType;
            return this;
        }

        public Builder setCarrierFrequencyHz(long carrierFrequencyHz) {
            if (carrierFrequencyHz < 0L) {
                throw new IllegalArgumentException("carrierFrequencyHz must be >= 0");
            }
            mCarrierFrequencyHz = carrierFrequencyHz;
            return this;
        }

        public GnssAutomaticGainControl build() {
            return new GnssAutomaticGainControl(
                    mLevelDb, mConstellationType, mCarrierFrequencyHz);
        }
    }
}
