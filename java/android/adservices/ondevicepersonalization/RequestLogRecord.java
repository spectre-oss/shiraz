// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Log record containing rows written during an {@link IsolatedWorker#onExecute} request. */
public final class RequestLogRecord implements Parcelable {
    private final Instant mTime;
    private final List<ContentValues> mRows;

    private RequestLogRecord(Instant time, List<ContentValues> rows) {
        this.mTime = time == null ? Instant.EPOCH : time;
        List<ContentValues> copy = new ArrayList<ContentValues>();
        if (rows != null) {
            for (ContentValues row : rows) {
                copy.add(new ContentValues(Objects.requireNonNull(row, "row")));
            }
        }
        this.mRows = Collections.unmodifiableList(copy);
    }

    public Instant getTime() {
        return mTime;
    }

    public List<ContentValues> getRows() {
        return mRows;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RequestLogRecord)) {
            return false;
        }
        RequestLogRecord that = (RequestLogRecord) o;
        return Objects.equals(mTime, that.mTime)
                && RequestToken.contentValuesListEqual(mRows, that.mRows);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTime, Integer.valueOf(RequestToken.contentValuesListHashCode(mRows)));
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        RequestToken.writeInstant(dest, mTime);
        dest.writeInt(mRows.size());
        for (ContentValues row : mRows) {
            RequestToken.writeContentValues(dest, row);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final class Builder {
        private List<ContentValues> mRows = new ArrayList<ContentValues>();

        public Builder() {
        }

        public Builder setRows(List<ContentValues> rows) {
            Objects.requireNonNull(rows, "rows");
            this.mRows = new ArrayList<ContentValues>(rows);
            return this;
        }

        public Builder addRow(ContentValues row) {
            this.mRows.add(Objects.requireNonNull(row, "row"));
            return this;
        }

        public RequestLogRecord build() {
            return new RequestLogRecord(Instant.EPOCH, mRows);
        }
    }

    public static final Parcelable.Creator<RequestLogRecord> CREATOR =
            new Parcelable.Creator<RequestLogRecord>() {
                @Override
                public RequestLogRecord createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    Instant time = RequestToken.readInstant(in);
                    int count = in.readInt();
                    List<ContentValues> rows = new ArrayList<ContentValues>(count);
                    for (int i = 0; i < count; i++) {
                        rows.add(RequestToken.readContentValues(in));
                    }
                    return new RequestLogRecord(time, rows);
                }

                @Override
                public RequestLogRecord[] newArray(int size) {
                    return new RequestLogRecord[size];
                }
            };
}
