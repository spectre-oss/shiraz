// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public final class GnssStatus implements Parcelable {
    public static final int CONSTELLATION_UNKNOWN = 0;
    public static final int CONSTELLATION_GPS = 1;
    public static final int CONSTELLATION_SBAS = 2;
    public static final int CONSTELLATION_GLONASS = 3;
    public static final int CONSTELLATION_QZSS = 4;
    public static final int CONSTELLATION_BEIDOU = 5;
    public static final int CONSTELLATION_GALILEO = 6;
    public static final int CONSTELLATION_IRNSS = 7;

    private static final int SVID_FLAGS_NONE = 0;
    private static final int SVID_FLAGS_HAS_EPHEMERIS_DATA = 1 << 0;
    private static final int SVID_FLAGS_HAS_ALMANAC_DATA = 1 << 1;
    private static final int SVID_FLAGS_USED_IN_FIX = 1 << 2;
    private static final int SVID_FLAGS_HAS_CARRIER_FREQUENCY = 1 << 3;
    private static final int SVID_FLAGS_HAS_BASEBAND_CN0 = 1 << 4;

    private static final int CONSTELLATION_TYPE_SHIFT_WIDTH = 8;
    private static final int CONSTELLATION_TYPE_MASK = 0xf;
    private static final int SVID_SHIFT_WIDTH = 12;

    public abstract static class Callback {
        public void onStarted() {
        }

        public void onStopped() {
        }

        public void onFirstFix(int ttffMillis) {
        }

        public void onSatelliteStatusChanged(GnssStatus status) {
        }
    }

    private final int mSvCount;
    private final int[] mSvidWithFlags;
    private final float[] mCn0DbHzs;
    private final float[] mElevations;
    private final float[] mAzimuths;
    private final float[] mCarrierFrequencies;
    private final float[] mBasebandCn0DbHzs;

    private GnssStatus(
            int svCount,
            int[] svidWithFlags,
            float[] cn0DbHzs,
            float[] elevations,
            float[] azimuths,
            float[] carrierFrequencies,
            float[] basebandCn0DbHzs) {
        mSvCount = svCount;
        mSvidWithFlags = svidWithFlags;
        mCn0DbHzs = cn0DbHzs;
        mElevations = elevations;
        mAzimuths = azimuths;
        mCarrierFrequencies = carrierFrequencies;
        mBasebandCn0DbHzs = basebandCn0DbHzs;
    }

    private void checkIndex(int satelliteIndex) {
        if (satelliteIndex < 0 || satelliteIndex >= mSvCount) {
            throw new IndexOutOfBoundsException("satelliteIndex=" + satelliteIndex);
        }
    }

    public int getSatelliteCount() {
        return mSvCount;
    }

    public int getConstellationType(int satelliteIndex) {
        checkIndex(satelliteIndex);
        return (mSvidWithFlags[satelliteIndex] >> CONSTELLATION_TYPE_SHIFT_WIDTH)
                & CONSTELLATION_TYPE_MASK;
    }

    public int getSvid(int satelliteIndex) {
        checkIndex(satelliteIndex);
        return mSvidWithFlags[satelliteIndex] >>> SVID_SHIFT_WIDTH;
    }

    public float getCn0DbHz(int satelliteIndex) {
        checkIndex(satelliteIndex);
        return mCn0DbHzs[satelliteIndex];
    }

    public float getElevationDegrees(int satelliteIndex) {
        checkIndex(satelliteIndex);
        return mElevations[satelliteIndex];
    }

    public float getAzimuthDegrees(int satelliteIndex) {
        checkIndex(satelliteIndex);
        return mAzimuths[satelliteIndex];
    }

    public boolean hasEphemerisData(int satelliteIndex) {
        checkIndex(satelliteIndex);
        return (mSvidWithFlags[satelliteIndex] & SVID_FLAGS_HAS_EPHEMERIS_DATA) != 0;
    }

    public boolean hasAlmanacData(int satelliteIndex) {
        checkIndex(satelliteIndex);
        return (mSvidWithFlags[satelliteIndex] & SVID_FLAGS_HAS_ALMANAC_DATA) != 0;
    }

    public boolean usedInFix(int satelliteIndex) {
        checkIndex(satelliteIndex);
        return (mSvidWithFlags[satelliteIndex] & SVID_FLAGS_USED_IN_FIX) != 0;
    }

    public boolean hasCarrierFrequencyHz(int satelliteIndex) {
        checkIndex(satelliteIndex);
        return (mSvidWithFlags[satelliteIndex] & SVID_FLAGS_HAS_CARRIER_FREQUENCY) != 0;
    }

    public float getCarrierFrequencyHz(int satelliteIndex) {
        checkIndex(satelliteIndex);
        return mCarrierFrequencies[satelliteIndex];
    }

    public boolean hasBasebandCn0DbHz(int satelliteIndex) {
        checkIndex(satelliteIndex);
        return (mSvidWithFlags[satelliteIndex] & SVID_FLAGS_HAS_BASEBAND_CN0) != 0;
    }

    public float getBasebandCn0DbHz(int satelliteIndex) {
        checkIndex(satelliteIndex);
        return mBasebandCn0DbHzs[satelliteIndex];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GnssStatus)) {
            return false;
        }
        GnssStatus that = (GnssStatus) o;
        return mSvCount == that.mSvCount
                && Arrays.equals(mSvidWithFlags, that.mSvidWithFlags)
                && Arrays.equals(mCn0DbHzs, that.mCn0DbHzs)
                && Arrays.equals(mElevations, that.mElevations)
                && Arrays.equals(mAzimuths, that.mAzimuths)
                && Arrays.equals(mCarrierFrequencies, that.mCarrierFrequencies)
                && Arrays.equals(mBasebandCn0DbHzs, that.mBasebandCn0DbHzs);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mSvCount);
        result = 31 * result + Arrays.hashCode(mSvidWithFlags);
        result = 31 * result + Arrays.hashCode(mCn0DbHzs);
        return result;
    }

    public static final Parcelable.Creator<GnssStatus> CREATOR =
            new Parcelable.Creator<GnssStatus>() {
                @Override
                public GnssStatus createFromParcel(Parcel in) {
                    int svCount = in.readInt();
                    int[] svidWithFlags = new int[svCount];
                    float[] cn0DbHzs = new float[svCount];
                    float[] elevations = new float[svCount];
                    float[] azimuths = new float[svCount];
                    float[] carrierFrequencies = new float[svCount];
                    float[] basebandCn0DbHzs = new float[svCount];
                    for (int i = 0; i < svCount; i++) {
                        svidWithFlags[i] = in.readInt();
                        cn0DbHzs[i] = in.readFloat();
                        elevations[i] = in.readFloat();
                        azimuths[i] = in.readFloat();
                        carrierFrequencies[i] = in.readFloat();
                        basebandCn0DbHzs[i] = in.readFloat();
                    }
                    return new GnssStatus(
                            svCount,
                            svidWithFlags,
                            cn0DbHzs,
                            elevations,
                            azimuths,
                            carrierFrequencies,
                            basebandCn0DbHzs);
                }

                @Override
                public GnssStatus[] newArray(int size) {
                    return new GnssStatus[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mSvCount);
        for (int i = 0; i < mSvCount; i++) {
            parcel.writeInt(mSvidWithFlags[i]);
            parcel.writeFloat(mCn0DbHzs[i]);
            parcel.writeFloat(mElevations[i]);
            parcel.writeFloat(mAzimuths[i]);
            parcel.writeFloat(mCarrierFrequencies[i]);
            parcel.writeFloat(mBasebandCn0DbHzs[i]);
        }
    }

    public static final class Builder {
        private final ArrayList<SatData> mSatellites = new ArrayList<SatData>();

        private static final class SatData {
            int svidWithFlags;
            float cn0DbHz;
            float elevation;
            float azimuth;
            float carrierFrequency;
            float basebandCn0DbHz;
        }

        public Builder() {
        }

        public Builder addSatellite(
                int constellationType,
                int svid,
                float cn0DbHz,
                float elevation,
                float azimuth,
                boolean hasEphemeris,
                boolean hasAlmanac,
                boolean usedInFix,
                boolean hasCarrierFrequency,
                float carrierFrequency,
                boolean hasBasebandCn0DbHz,
                float basebandCn0DbHz) {
            int flags = SVID_FLAGS_NONE;
            if (hasEphemeris) flags |= SVID_FLAGS_HAS_EPHEMERIS_DATA;
            if (hasAlmanac) flags |= SVID_FLAGS_HAS_ALMANAC_DATA;
            if (usedInFix) flags |= SVID_FLAGS_USED_IN_FIX;
            if (hasCarrierFrequency) flags |= SVID_FLAGS_HAS_CARRIER_FREQUENCY;
            if (hasBasebandCn0DbHz) flags |= SVID_FLAGS_HAS_BASEBAND_CN0;

            SatData sat = new SatData();
            sat.svidWithFlags = (svid << SVID_SHIFT_WIDTH)
                    | ((constellationType & CONSTELLATION_TYPE_MASK)
                            << CONSTELLATION_TYPE_SHIFT_WIDTH)
                    | flags;
            sat.cn0DbHz = cn0DbHz;
            sat.elevation = elevation;
            sat.azimuth = azimuth;
            sat.carrierFrequency = hasCarrierFrequency ? carrierFrequency : 0.0f;
            sat.basebandCn0DbHz = hasBasebandCn0DbHz ? basebandCn0DbHz : 0.0f;
            mSatellites.add(sat);
            return this;
        }

        public Builder clearSatellites() {
            mSatellites.clear();
            return this;
        }

        public GnssStatus build() {
            int count = mSatellites.size();
            int[] svidWithFlags = new int[count];
            float[] cn0DbHzs = new float[count];
            float[] elevations = new float[count];
            float[] azimuths = new float[count];
            float[] carrierFrequencies = new float[count];
            float[] basebandCn0DbHzs = new float[count];
            for (int i = 0; i < count; i++) {
                SatData sat = mSatellites.get(i);
                svidWithFlags[i] = sat.svidWithFlags;
                cn0DbHzs[i] = sat.cn0DbHz;
                elevations[i] = sat.elevation;
                azimuths[i] = sat.azimuth;
                carrierFrequencies[i] = sat.carrierFrequency;
                basebandCn0DbHzs[i] = sat.basebandCn0DbHz;
            }
            return new GnssStatus(
                    count,
                    svidWithFlags,
                    cn0DbHzs,
                    elevations,
                    azimuths,
                    carrierFrequencies,
                    basebandCn0DbHzs);
        }
    }
}
