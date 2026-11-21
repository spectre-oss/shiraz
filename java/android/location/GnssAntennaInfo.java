// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class GnssAntennaInfo implements Parcelable {
    private final double mCarrierFrequencyMHz;
    private final PhaseCenterOffset mPhaseCenterOffset;
    private final SphericalCorrections mPhaseCenterVariationCorrections;
    private final SphericalCorrections mSignalGainCorrections;

    public interface Listener {
        void onGnssAntennaInfoReceived(List<GnssAntennaInfo> gnssAntennaInfos);
    }

    public static final class PhaseCenterOffset implements Parcelable {
        private final double mOffsetXMm;
        private final double mOffsetXUncertaintyMm;
        private final double mOffsetYMm;
        private final double mOffsetYUncertaintyMm;
        private final double mOffsetZMm;
        private final double mOffsetZUncertaintyMm;

        public PhaseCenterOffset(
                double offsetXMm,
                double offsetXUncertaintyMm,
                double offsetYMm,
                double offsetYUncertaintyMm,
                double offsetZMm,
                double offsetZUncertaintyMm) {
            mOffsetXMm = offsetXMm;
            mOffsetYMm = offsetYMm;
            mOffsetZMm = offsetZMm;
            mOffsetXUncertaintyMm = offsetXUncertaintyMm;
            mOffsetYUncertaintyMm = offsetYUncertaintyMm;
            mOffsetZUncertaintyMm = offsetZUncertaintyMm;
        }

        public static final Parcelable.Creator<PhaseCenterOffset> CREATOR =
                new Parcelable.Creator<PhaseCenterOffset>() {
                    @Override
                    public PhaseCenterOffset createFromParcel(Parcel in) {
                        return new PhaseCenterOffset(
                                in.readDouble(),
                                in.readDouble(),
                                in.readDouble(),
                                in.readDouble(),
                                in.readDouble(),
                                in.readDouble());
                    }

                    @Override
                    public PhaseCenterOffset[] newArray(int size) {
                        return new PhaseCenterOffset[size];
                    }
                };

        public double getXOffsetMm() {
            return mOffsetXMm;
        }

        public double getXOffsetUncertaintyMm() {
            return mOffsetXUncertaintyMm;
        }

        public double getYOffsetMm() {
            return mOffsetYMm;
        }

        public double getYOffsetUncertaintyMm() {
            return mOffsetYUncertaintyMm;
        }

        public double getZOffsetMm() {
            return mOffsetZMm;
        }

        public double getZOffsetUncertaintyMm() {
            return mOffsetZUncertaintyMm;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeDouble(mOffsetXMm);
            dest.writeDouble(mOffsetXUncertaintyMm);
            dest.writeDouble(mOffsetYMm);
            dest.writeDouble(mOffsetYUncertaintyMm);
            dest.writeDouble(mOffsetZMm);
            dest.writeDouble(mOffsetZUncertaintyMm);
        }

        @Override
        public String toString() {
            return "PhaseCenterOffset{"
                    + "OffsetXMm=" + mOffsetXMm + " +/-" + mOffsetXUncertaintyMm
                    + ", OffsetYMm=" + mOffsetYMm + " +/-" + mOffsetYUncertaintyMm
                    + ", OffsetZMm=" + mOffsetZMm + " +/-" + mOffsetZUncertaintyMm
                    + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PhaseCenterOffset)) {
                return false;
            }
            PhaseCenterOffset that = (PhaseCenterOffset) o;
            return Double.compare(that.mOffsetXMm, mOffsetXMm) == 0
                    && Double.compare(that.mOffsetXUncertaintyMm, mOffsetXUncertaintyMm) == 0
                    && Double.compare(that.mOffsetYMm, mOffsetYMm) == 0
                    && Double.compare(that.mOffsetYUncertaintyMm, mOffsetYUncertaintyMm) == 0
                    && Double.compare(that.mOffsetZMm, mOffsetZMm) == 0
                    && Double.compare(that.mOffsetZUncertaintyMm, mOffsetZUncertaintyMm) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mOffsetXMm, mOffsetYMm, mOffsetZMm);
        }
    }

    public static final class SphericalCorrections implements Parcelable {
        private final double[][] mCorrections;
        private final double[][] mCorrectionUncertainties;
        private final double mDeltaTheta;
        private final double mDeltaPhi;
        private final int mNumRows;
        private final int mNumColumns;

        public SphericalCorrections(
                double[][] corrections, double[][] correctionUncertainties) {
            if (corrections == null || correctionUncertainties == null || corrections.length < 1) {
                throw new IllegalArgumentException(
                        "corrections and correctionUncertainties must be non-null");
            }
            mNumRows = corrections.length;
            mNumColumns = corrections[0] == null ? 0 : corrections[0].length;
            if (mNumRows < 2 || mNumColumns < 2
                    || correctionUncertainties.length != mNumRows) {
                throw new IllegalArgumentException(
                        "corrections and correctionUncertainties must have >= 2 rows and columns");
            }
            mCorrections = new double[mNumRows][mNumColumns];
            mCorrectionUncertainties = new double[mNumRows][mNumColumns];
            for (int r = 0; r < mNumRows; r++) {
                if (corrections[r] == null || corrections[r].length != mNumColumns
                        || correctionUncertainties[r] == null
                        || correctionUncertainties[r].length != mNumColumns) {
                    throw new IllegalArgumentException(
                            "corrections and correctionUncertainties must be rectangular");
                }
                System.arraycopy(corrections[r], 0, mCorrections[r], 0, mNumColumns);
                System.arraycopy(
                        correctionUncertainties[r], 0,
                        mCorrectionUncertainties[r], 0, mNumColumns);
            }
            mDeltaTheta = 180.0 / (mNumRows - 1);
            mDeltaPhi = 360.0 / mNumColumns;
        }

        private SphericalCorrections(Parcel in) {
            int numRows = in.readInt();
            int numColumns = in.readInt();
            double[][] corrections = new double[numRows][numColumns];
            double[][] correctionUncertainties = new double[numRows][numColumns];
            for (int row = 0; row < numRows; row++) {
                in.readDoubleArray(corrections[row]);
            }
            for (int row = 0; row < numRows; row++) {
                in.readDoubleArray(correctionUncertainties[row]);
            }
            mNumRows = numRows;
            mNumColumns = numColumns;
            mCorrections = corrections;
            mCorrectionUncertainties = correctionUncertainties;
            mDeltaTheta = 180.0 / (mNumRows - 1);
            mDeltaPhi = 360.0 / mNumColumns;
        }

        public double[][] getCorrectionsArray() {
            double[][] copy = new double[mNumRows][mNumColumns];
            for (int r = 0; r < mNumRows; r++) {
                System.arraycopy(mCorrections[r], 0, copy[r], 0, mNumColumns);
            }
            return copy;
        }

        public double[][] getCorrectionUncertaintiesArray() {
            double[][] copy = new double[mNumRows][mNumColumns];
            for (int r = 0; r < mNumRows; r++) {
                System.arraycopy(mCorrectionUncertainties[r], 0, copy[r], 0, mNumColumns);
            }
            return copy;
        }

        public double getDeltaTheta() {
            return mDeltaTheta;
        }

        public double getDeltaPhi() {
            return mDeltaPhi;
        }

        public static final Parcelable.Creator<SphericalCorrections> CREATOR =
                new Parcelable.Creator<SphericalCorrections>() {
                    @Override
                    public SphericalCorrections createFromParcel(Parcel in) {
                        return new SphericalCorrections(in);
                    }

                    @Override
                    public SphericalCorrections[] newArray(int size) {
                        return new SphericalCorrections[size];
                    }
                };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mNumRows);
            dest.writeInt(mNumColumns);
            for (int row = 0; row < mNumRows; row++) {
                dest.writeDoubleArray(mCorrections[row]);
            }
            for (int row = 0; row < mNumRows; row++) {
                dest.writeDoubleArray(mCorrectionUncertainties[row]);
            }
        }

        @Override
        public String toString() {
            return "SphericalCorrections{DeltaTheta=" + mDeltaTheta
                    + ", DeltaPhi=" + mDeltaPhi + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SphericalCorrections)) {
                return false;
            }
            SphericalCorrections that = (SphericalCorrections) o;
            return mNumRows == that.mNumRows
                    && mNumColumns == that.mNumColumns
                    && Arrays.deepEquals(mCorrections, that.mCorrections)
                    && Arrays.deepEquals(
                            mCorrectionUncertainties, that.mCorrectionUncertainties);
        }

        @Override
        public int hashCode() {
            int result = Arrays.deepHashCode(mCorrections);
            result = 31 * result + Arrays.deepHashCode(mCorrectionUncertainties);
            return result;
        }
    }

    private GnssAntennaInfo(
            double carrierFrequencyMHz,
            PhaseCenterOffset phaseCenterOffset,
            SphericalCorrections phaseCenterVariationCorrections,
            SphericalCorrections signalGainCorrectionDbi) {
        mCarrierFrequencyMHz = carrierFrequencyMHz;
        mPhaseCenterOffset = Objects.requireNonNull(phaseCenterOffset);
        mPhaseCenterVariationCorrections = phaseCenterVariationCorrections;
        mSignalGainCorrections = signalGainCorrectionDbi;
    }

    public static class Builder {
        private double mCarrierFrequencyMHz;
        private PhaseCenterOffset mPhaseCenterOffset;
        private SphericalCorrections mPhaseCenterVariationCorrections;
        private SphericalCorrections mSignalGainCorrections;

        @Deprecated
        public Builder() {
            this(0.0, new PhaseCenterOffset(0, 0, 0, 0, 0, 0));
        }

        public Builder(double carrierFrequencyMHz, PhaseCenterOffset phaseCenterOffset) {
            mCarrierFrequencyMHz = carrierFrequencyMHz;
            mPhaseCenterOffset = Objects.requireNonNull(phaseCenterOffset);
        }

        public Builder(GnssAntennaInfo antennaInfo) {
            Objects.requireNonNull(antennaInfo);
            mCarrierFrequencyMHz = antennaInfo.mCarrierFrequencyMHz;
            mPhaseCenterOffset = antennaInfo.mPhaseCenterOffset;
            mPhaseCenterVariationCorrections = antennaInfo.mPhaseCenterVariationCorrections;
            mSignalGainCorrections = antennaInfo.mSignalGainCorrections;
        }

        public Builder setCarrierFrequencyMHz(double carrierFrequencyMHz) {
            mCarrierFrequencyMHz = carrierFrequencyMHz;
            return this;
        }

        public Builder setPhaseCenterOffset(PhaseCenterOffset phaseCenterOffset) {
            mPhaseCenterOffset = Objects.requireNonNull(phaseCenterOffset);
            return this;
        }

        public Builder setPhaseCenterVariationCorrections(
                SphericalCorrections phaseCenterVariationCorrections) {
            mPhaseCenterVariationCorrections = phaseCenterVariationCorrections;
            return this;
        }

        public Builder setSignalGainCorrections(SphericalCorrections signalGainCorrections) {
            mSignalGainCorrections = signalGainCorrections;
            return this;
        }

        public GnssAntennaInfo build() {
            return new GnssAntennaInfo(
                    mCarrierFrequencyMHz,
                    mPhaseCenterOffset,
                    mPhaseCenterVariationCorrections,
                    mSignalGainCorrections);
        }
    }

    public double getCarrierFrequencyMHz() {
        return mCarrierFrequencyMHz;
    }

    public PhaseCenterOffset getPhaseCenterOffset() {
        return mPhaseCenterOffset;
    }

    public SphericalCorrections getPhaseCenterVariationCorrections() {
        return mPhaseCenterVariationCorrections;
    }

    public SphericalCorrections getSignalGainCorrections() {
        return mSignalGainCorrections;
    }

    public static final Parcelable.Creator<GnssAntennaInfo> CREATOR =
            new Parcelable.Creator<GnssAntennaInfo>() {
                @Override
                public GnssAntennaInfo createFromParcel(Parcel in) {
                    double carrierFrequencyMHz = in.readDouble();
                    PhaseCenterOffset phaseCenterOffset =
                            in.readTypedObject(PhaseCenterOffset.CREATOR);
                    SphericalCorrections phaseCenterVariationCorrections =
                            in.readTypedObject(SphericalCorrections.CREATOR);
                    SphericalCorrections signalGainCorrections =
                            in.readTypedObject(SphericalCorrections.CREATOR);
                    return new GnssAntennaInfo(
                            carrierFrequencyMHz,
                            phaseCenterOffset,
                            phaseCenterVariationCorrections,
                            signalGainCorrections);
                }

                @Override
                public GnssAntennaInfo[] newArray(int size) {
                    return new GnssAntennaInfo[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeDouble(mCarrierFrequencyMHz);
        parcel.writeTypedObject(mPhaseCenterOffset, flags);
        parcel.writeTypedObject(mPhaseCenterVariationCorrections, flags);
        parcel.writeTypedObject(mSignalGainCorrections, flags);
    }

    @Override
    public String toString() {
        return "GnssAntennaInfo{"
                + "CarrierFrequencyMHz=" + mCarrierFrequencyMHz
                + ", PhaseCenterOffset=" + mPhaseCenterOffset
                + ", PhaseCenterVariationCorrections=" + mPhaseCenterVariationCorrections
                + ", SignalGainCorrections=" + mSignalGainCorrections
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GnssAntennaInfo)) {
            return false;
        }
        GnssAntennaInfo that = (GnssAntennaInfo) o;
        return Double.compare(that.mCarrierFrequencyMHz, mCarrierFrequencyMHz) == 0
                && mPhaseCenterOffset.equals(that.mPhaseCenterOffset)
                && Objects.equals(
                        mPhaseCenterVariationCorrections,
                        that.mPhaseCenterVariationCorrections)
                && Objects.equals(mSignalGainCorrections, that.mSignalGainCorrections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mCarrierFrequencyMHz,
                mPhaseCenterOffset,
                mPhaseCenterVariationCorrections,
                mSignalGainCorrections);
    }
}
