// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Length;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class HeightRecord extends InstantRecord {
    public static final AggregationType<Length> HEIGHT_AVG =
            new AggregationType<Length>(HeightRecord.class, "height",
                    AggregationType.OP_AVG, AggregationType.KIND_LENGTH);
    public static final AggregationType<Length> HEIGHT_MAX =
            new AggregationType<Length>(HeightRecord.class, "height",
                    AggregationType.OP_MAX, AggregationType.KIND_LENGTH);
    public static final AggregationType<Length> HEIGHT_MIN =
            new AggregationType<Length>(HeightRecord.class, "height",
                    AggregationType.OP_MIN, AggregationType.KIND_LENGTH);

    private final Length mHeight;

    HeightRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset, Length height) {
        super(metadata, time, zoneOffset);
        mHeight = Objects.requireNonNull(height, "height must not be null");
    }

    public Length getHeight() {
        return mHeight;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("height".equals(metric)) {
            accumulator.addDouble(mHeight.getInMeters());
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mHeight, ((HeightRecord) object).mHeight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mHeight);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final Length mHeight;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, Length height) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            mHeight = Objects.requireNonNull(height, "height must not be null");
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public HeightRecord build() {
            return new HeightRecord(mMetadata, mTime, mZoneOffset, mHeight);
        }
    }
}
