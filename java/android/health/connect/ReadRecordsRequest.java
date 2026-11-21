// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect;

import android.health.connect.datatypes.Record;
import java.util.Objects;

public abstract class ReadRecordsRequest<T extends Record> {
    private final Class<T> mRecordType;

    ReadRecordsRequest(Class<T> recordType) {
        mRecordType = Objects.requireNonNull(recordType, "recordType must not be null");
    }

    public Class<T> getRecordType() {
        return mRecordType;
    }
}
