// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.util.Objects;

public abstract class Record {
    private Metadata mMetadata;

    Record(Metadata metadata) {
        mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
    }

    public Metadata getMetadata() {
        return mMetadata;
    }

    public void shirazAssignMetadata(Metadata metadata) {
        mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
    }

    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        return Objects.equals(mMetadata, ((Record) object).mMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mMetadata);
    }
}
