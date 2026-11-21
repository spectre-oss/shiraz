// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.changelog;

import android.health.connect.datatypes.Record;
import android.os.Parcel;
import android.os.Parcelable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChangeLogsResponse implements Parcelable {
    private static final AtomicInteger NEXT_SNAPSHOT_ID = new AtomicInteger(1);
    private static final Map<Integer, List<Record>> UPSERTED_SNAPSHOTS =
            Collections.synchronizedMap(new HashMap<Integer, List<Record>>());

    public static final Parcelable.Creator<ChangeLogsResponse> CREATOR =
            new Parcelable.Creator<ChangeLogsResponse>() {
                @Override
                public ChangeLogsResponse createFromParcel(Parcel in) {
                    int snapshotId = in.readInt();
                    List<Record> upserted = UPSERTED_SNAPSHOTS.get(Integer.valueOf(snapshotId));
                    if (upserted == null) {
                        upserted = Collections.emptyList();
                    }
                    int delCount = in.readInt();
                    List<DeletedLog> deleted = new ArrayList<DeletedLog>(Math.max(0, delCount));
                    for (int i = 0; i < delCount; i++) {
                        String id = in.readString();
                        long millis = in.readLong();
                        deleted.add(new DeletedLog(id != null ? id : "", millis));
                    }
                    String nextToken = in.readString();
                    boolean hasMore = in.readInt() != 0;
                    return new ChangeLogsResponse(upserted, deleted,
                            nextToken != null ? nextToken : "", hasMore);
                }

                @Override
                public ChangeLogsResponse[] newArray(int size) {
                    return new ChangeLogsResponse[size];
                }
            };

    private final List<Record> mUpsertedRecords;
    private final List<DeletedLog> mDeletedLogs;
    private final String mNextChangesToken;
    private final boolean mHasMorePages;

    public ChangeLogsResponse(List<Record> upsertedRecords, List<DeletedLog> deletedLogs,
            String nextChangesToken, boolean hasMorePages) {
        mUpsertedRecords = Collections.unmodifiableList(new ArrayList<Record>(
                Objects.requireNonNull(upsertedRecords, "upsertedRecords must not be null")));
        mDeletedLogs = Collections.unmodifiableList(new ArrayList<DeletedLog>(
                Objects.requireNonNull(deletedLogs, "deletedLogs must not be null")));
        mNextChangesToken = Objects.requireNonNull(nextChangesToken, "nextChangesToken must not be null");
        mHasMorePages = hasMorePages;
    }

    public List<Record> getUpsertedRecords() {
        return mUpsertedRecords;
    }

    public List<DeletedLog> getDeletedLogs() {
        return mDeletedLogs;
    }

    public String getNextChangesToken() {
        return mNextChangesToken;
    }

    public boolean hasMorePages() {
        return mHasMorePages;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        int id = NEXT_SNAPSHOT_ID.getAndIncrement();
        UPSERTED_SNAPSHOTS.put(Integer.valueOf(id), mUpsertedRecords);
        dest.writeInt(id);
        dest.writeInt(mDeletedLogs.size());
        for (int i = 0; i < mDeletedLogs.size(); i++) {
            DeletedLog log = mDeletedLogs.get(i);
            dest.writeString(log.getDeletedRecordId());
            dest.writeLong(log.getDeletedTime().toEpochMilli());
        }
        dest.writeString(mNextChangesToken);
        dest.writeInt(mHasMorePages ? 1 : 0);
    }

    public static final class DeletedLog {
        private final String mDeletedRecordId;
        private final Instant mDeletedTime;

        public DeletedLog(String deletedRecordId, long deletedTimeMillis) {
            mDeletedRecordId = Objects.requireNonNull(deletedRecordId, "deletedRecordId must not be null");
            mDeletedTime = Instant.ofEpochMilli(deletedTimeMillis);
        }

        public String getDeletedRecordId() {
            return mDeletedRecordId;
        }

        public Instant getDeletedTime() {
            return mDeletedTime;
        }
    }
}
