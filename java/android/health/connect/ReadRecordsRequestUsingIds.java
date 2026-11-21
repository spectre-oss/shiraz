// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect;

import android.health.connect.datatypes.Record;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ReadRecordsRequestUsingIds<T extends Record> extends ReadRecordsRequest<T> {
    private final List<RecordIdFilter> mRecordIdFilters;

    ReadRecordsRequestUsingIds(Class<T> recordType, List<RecordIdFilter> recordIdFilters) {
        super(recordType);
        mRecordIdFilters = Collections.unmodifiableList(
                new ArrayList<RecordIdFilter>(recordIdFilters));
    }

    public List<RecordIdFilter> getRecordIdFilters() {
        return mRecordIdFilters;
    }

    public static final class Builder<T extends Record> {
        private final Class<T> mRecordType;
        private final List<RecordIdFilter> mRecordIdFilters = new ArrayList<RecordIdFilter>();

        public Builder(Class<T> recordType) {
            mRecordType = Objects.requireNonNull(recordType, "recordType must not be null");
        }

        public Builder<T> addId(String id) {
            mRecordIdFilters.add(RecordIdFilter.fromId(mRecordType, id));
            return this;
        }

        public Builder<T> addClientRecordId(String clientRecordId) {
            mRecordIdFilters.add(RecordIdFilter.fromClientRecordId(mRecordType, clientRecordId));
            return this;
        }

        public ReadRecordsRequestUsingIds<T> build() {
            if (mRecordIdFilters.isEmpty()) {
                throw new IllegalArgumentException("At least one id or clientRecordId is required");
            }
            return new ReadRecordsRequestUsingIds<T>(mRecordType, mRecordIdFilters);
        }
    }
}
