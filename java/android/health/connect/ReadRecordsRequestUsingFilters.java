// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect;

import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Record;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ReadRecordsRequestUsingFilters<T extends Record> extends ReadRecordsRequest<T> {
    private final TimeRangeFilter mTimeRangeFilter;
    private final Set<DataOrigin> mDataOrigins;
    private final int mPageSize;
    private final long mPageToken;
    private final boolean mAscending;

    ReadRecordsRequestUsingFilters(Class<T> recordType, TimeRangeFilter timeRangeFilter,
            Set<DataOrigin> dataOrigins, int pageSize, long pageToken, boolean ascending) {
        super(recordType);
        mTimeRangeFilter = timeRangeFilter;
        mDataOrigins = Collections.unmodifiableSet(new LinkedHashSet<DataOrigin>(dataOrigins));
        mPageSize = pageSize;
        mPageToken = pageToken;
        mAscending = ascending;
    }

    public TimeRangeFilter getTimeRangeFilter() {
        return mTimeRangeFilter;
    }

    public Set<DataOrigin> getDataOrigins() {
        return mDataOrigins;
    }

    public int getPageSize() {
        return mPageSize;
    }

    public long getPageToken() {
        return mPageToken;
    }

    public boolean isAscending() {
        return mAscending;
    }

    public static final class Builder<T extends Record> {
        private final Class<T> mRecordType;
        private final Set<DataOrigin> mDataOrigins = new LinkedHashSet<DataOrigin>();
        private TimeRangeFilter mTimeRangeFilter;
        private int mPageSize = 1000;
        private long mPageToken = -1L;
        private boolean mAscending = true;
        private boolean mAscendingExplicitlySet;

        public Builder(Class<T> recordType) {
            mRecordType = Objects.requireNonNull(recordType, "recordType must not be null");
        }

        public Builder<T> addDataOrigins(DataOrigin dataOrigin) {
            Objects.requireNonNull(dataOrigin, "dataOrigin must not be null");
            mDataOrigins.add(dataOrigin);
            return this;
        }

        public Builder<T> setTimeRangeFilter(TimeRangeFilter timeRangeFilter) {
            mTimeRangeFilter = timeRangeFilter;
            return this;
        }

        public Builder<T> setPageSize(int pageSize) {
            if (pageSize < 1 || pageSize > 5000) {
                throw new IllegalArgumentException("pageSize must be in [1, 5000]");
            }
            mPageSize = pageSize;
            return this;
        }

        public Builder<T> setPageToken(long pageToken) {
            mPageToken = pageToken;
            return this;
        }

        public Builder<T> setAscending(boolean ascending) {
            mAscending = ascending;
            mAscendingExplicitlySet = true;
            return this;
        }

        public ReadRecordsRequestUsingFilters<T> build() {
            if (mPageToken != -1L && mAscendingExplicitlySet) {
                throw new IllegalStateException("Cannot set both pageToken and ascending");
            }
            return new ReadRecordsRequestUsingFilters<T>(mRecordType, mTimeRangeFilter,
                    mDataOrigins, mPageSize, mPageToken, mAscending);
        }
    }
}
