// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect;

import android.health.connect.datatypes.Record;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class InsertRecordsResponse {
    private final List<Record> mRecords;

    public InsertRecordsResponse(List<Record> records) {
        mRecords = Collections.unmodifiableList(
                new ArrayList<Record>(Objects.requireNonNull(records, "records must not be null")));
    }

    public List<Record> getRecords() {
        return mRecords;
    }
}
