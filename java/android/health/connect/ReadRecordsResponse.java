// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect;

import android.health.connect.datatypes.Record;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ReadRecordsResponse<T extends Record> {
    private final List<T> mRecords;
    private final long mNextPageToken;

    public ReadRecordsResponse(List<T> records, long nextPageToken) {
        mRecords = Collections.unmodifiableList(
                new ArrayList<T>(Objects.requireNonNull(records, "records must not be null")));
        mNextPageToken = nextPageToken;
    }

    public List<T> getRecords() {
        return mRecords;
    }

    public long getNextPageToken() {
        return mNextPageToken;
    }
}
