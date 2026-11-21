// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect;

import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.DataOrigin;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class AggregateRecordsRequest<T> {
    private final TimeRangeFilter mTimeRangeFilter;
    private final Set<AggregationType<T>> mAggregationTypes;
    private final Set<DataOrigin> mDataOriginsFilters;

    AggregateRecordsRequest(TimeRangeFilter timeRangeFilter,
            Set<AggregationType<T>> aggregationTypes, Set<DataOrigin> dataOriginsFilters) {
        mTimeRangeFilter = Objects.requireNonNull(timeRangeFilter, "timeRangeFilter must not be null");
        mAggregationTypes = Collections.unmodifiableSet(
                new LinkedHashSet<AggregationType<T>>(aggregationTypes));
        mDataOriginsFilters = Collections.unmodifiableSet(
                new LinkedHashSet<DataOrigin>(dataOriginsFilters));
    }

    public TimeRangeFilter getTimeRangeFilter() {
        return mTimeRangeFilter;
    }

    public Set<AggregationType<T>> getAggregationTypes() {
        return mAggregationTypes;
    }

    public Set<DataOrigin> getDataOriginsFilters() {
        return mDataOriginsFilters;
    }

    public static final class Builder<T> {
        private final TimeRangeFilter mTimeRangeFilter;
        private final Set<AggregationType<T>> mAggregationTypes =
                new LinkedHashSet<AggregationType<T>>();
        private final Set<DataOrigin> mDataOriginsFilters = new LinkedHashSet<DataOrigin>();

        public Builder(TimeRangeFilter timeRangeFilter) {
            mTimeRangeFilter = Objects.requireNonNull(timeRangeFilter, "timeRangeFilter must not be null");
        }

        public Builder<T> addAggregationType(AggregationType<T> aggregationType) {
            Objects.requireNonNull(aggregationType, "aggregationType must not be null");
            mAggregationTypes.add(aggregationType);
            return this;
        }

        public Builder<T> addDataOriginsFilter(DataOrigin dataOriginsFilter) {
            Objects.requireNonNull(dataOriginsFilter, "dataOriginsFilter must not be null");
            mDataOriginsFilters.add(dataOriginsFilter);
            return this;
        }

        public AggregateRecordsRequest<T> build() {
            if (mAggregationTypes.isEmpty()) {
                throw new IllegalArgumentException("At least one aggregationType must be added");
            }
            return new AggregateRecordsRequest<T>(
                    mTimeRangeFilter, mAggregationTypes, mDataOriginsFilters);
        }
    }
}
