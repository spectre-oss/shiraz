// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class Vo2MaxRecord extends InstantRecord {
    private final int mMeasurementMethod;
    private final double mVo2MillilitersPerMinuteKilogram;

    Vo2MaxRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset,
            int measurementMethod, double vo2MillilitersPerMinuteKilogram) {
        super(metadata, time, zoneOffset);
        if (vo2MillilitersPerMinuteKilogram < 0.0 || vo2MillilitersPerMinuteKilogram > 100.0) {
            throw new IllegalArgumentException("vo2MillilitersPerMinuteKilogram must be in [0.0, 100.0]");
        }
        mMeasurementMethod = measurementMethod;
        mVo2MillilitersPerMinuteKilogram = vo2MillilitersPerMinuteKilogram;
    }

    public int getMeasurementMethod() {
        return mMeasurementMethod;
    }

    public double getVo2MillilitersPerMinuteKilogram() {
        return mVo2MillilitersPerMinuteKilogram;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        Vo2MaxRecord that = (Vo2MaxRecord) object;
        return mMeasurementMethod == that.mMeasurementMethod
                && Double.compare(mVo2MillilitersPerMinuteKilogram,
                        that.mVo2MillilitersPerMinuteKilogram) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mMeasurementMethod, mVo2MillilitersPerMinuteKilogram);
    }

    public static final class Vo2MaxMeasurementMethod {
        public static final int MEASUREMENT_METHOD_OTHER = 0;
        public static final int MEASUREMENT_METHOD_METABOLIC_CART = 1;
        public static final int MEASUREMENT_METHOD_HEART_RATE_RATIO = 2;
        public static final int MEASUREMENT_METHOD_COOPER_TEST = 3;
        public static final int MEASUREMENT_METHOD_MULTISTAGE_FITNESS_TEST = 4;
        public static final int MEASUREMENT_METHOD_ROCKPORT_FITNESS_TEST = 5;

        private Vo2MaxMeasurementMethod() {
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final int mMeasurementMethod;
        private final double mVo2MillilitersPerMinuteKilogram;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, int measurementMethod,
                double vo2MillilitersPerMinuteKilogram) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            if (vo2MillilitersPerMinuteKilogram < 0.0 || vo2MillilitersPerMinuteKilogram > 100.0) {
                throw new IllegalArgumentException("vo2MillilitersPerMinuteKilogram must be in [0.0, 100.0]");
            }
            mMeasurementMethod = measurementMethod;
            mVo2MillilitersPerMinuteKilogram = vo2MillilitersPerMinuteKilogram;
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public Vo2MaxRecord build() {
            return new Vo2MaxRecord(mMetadata, mTime, mZoneOffset,
                    mMeasurementMethod, mVo2MillilitersPerMinuteKilogram);
        }
    }
}
