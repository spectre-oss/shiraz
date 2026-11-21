// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect;

import android.health.connect.datatypes.Record;
import java.util.Objects;

public class RecordIdFilter {
    private final Class<? extends Record> mRecordType;
    private final String mId;
    private final String mClientRecordId;

    private RecordIdFilter(Class<? extends Record> recordType, String id, String clientRecordId) {
        mRecordType = Objects.requireNonNull(recordType, "recordType must not be null");
        mId = id;
        mClientRecordId = clientRecordId;
    }

    public static RecordIdFilter fromClientRecordId(Class<? extends Record> recordType,
            String clientRecordId) {
        Objects.requireNonNull(clientRecordId, "clientRecordId must not be null");
        return new RecordIdFilter(recordType, null, clientRecordId);
    }

    public static RecordIdFilter fromId(Class<? extends Record> recordType, String id) {
        Objects.requireNonNull(id, "id must not be null");
        return new RecordIdFilter(recordType, id, null);
    }

    public Class<? extends Record> getRecordType() {
        return mRecordType;
    }

    public String getId() {
        return mId;
    }

    public String getClientRecordId() {
        return mClientRecordId;
    }
}
