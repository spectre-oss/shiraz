// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;
import java.time.Instant;
import java.util.Objects;

/** Event log record associated with a {@link RequestLogRecord} row. */
public final class EventLogRecord implements Parcelable {
    private final Instant mTime;
    private final int mRowIndex;
    private final int mType;
    private final ContentValues mData;
    private final RequestLogRecord mRequestLogRecord;

    private EventLogRecord(
            Instant time,
            int rowIndex,
            int type,
            ContentValues data,
            RequestLogRecord requestLogRecord) {
        if (rowIndex < 0) {
            throw new IllegalArgumentException("rowIndex must be non-negative");
        }
        if (type <= 0 || type >= 128) {
            throw new IllegalArgumentException("type must be in (0, 128)");
        }
        this.mTime = time == null ? Instant.EPOCH : time;
        this.mRowIndex = rowIndex;
        this.mType = type;
        this.mData = data == null ? null : new ContentValues(data);
        this.mRequestLogRecord = requestLogRecord;
    }

    public Instant getTime() {
        return mTime;
    }

    public int getRowIndex() {
        return mRowIndex;
    }

    public int getType() {
        return mType;
    }

    public ContentValues getData() {
        return mData == null ? null : new ContentValues(mData);
    }

    public RequestLogRecord getRequestLogRecord() {
        return mRequestLogRecord;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EventLogRecord)) {
            return false;
        }
        EventLogRecord that = (EventLogRecord) o;
        return mRowIndex == that.mRowIndex
                && mType == that.mType
                && Objects.equals(mTime, that.mTime)
                && RequestToken.contentValuesEqual(mData, that.mData)
                && Objects.equals(mRequestLogRecord, that.mRequestLogRecord);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTime,
                Integer.valueOf(mRowIndex),
                Integer.valueOf(mType),
                Integer.valueOf(RequestToken.contentValuesHashCode(mData)),
                mRequestLogRecord);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        RequestToken.writeInstant(dest, mTime);
        dest.writeInt(mRowIndex);
        dest.writeInt(mType);
        RequestToken.writeContentValues(dest, mData);
        dest.writeTypedObject(mRequestLogRecord, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final class Builder {
        private int mRowIndex;
        private int mType = 1;
        private ContentValues mData;
        private RequestLogRecord mRequestLogRecord;

        public Builder() {
        }

        public Builder setRowIndex(int rowIndex) {
            this.mRowIndex = rowIndex;
            return this;
        }

        public Builder setType(int type) {
            this.mType = type;
            return this;
        }

        public Builder setData(ContentValues data) {
            this.mData = data == null ? null : new ContentValues(data);
            return this;
        }

        public Builder setRequestLogRecord(RequestLogRecord requestLogRecord) {
            this.mRequestLogRecord = requestLogRecord;
            return this;
        }

        public EventLogRecord build() {
            return new EventLogRecord(
                    Instant.EPOCH, mRowIndex, mType, mData, mRequestLogRecord);
        }
    }

    public static final Parcelable.Creator<EventLogRecord> CREATOR =
            new Parcelable.Creator<EventLogRecord>() {
                @Override
                public EventLogRecord createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    Instant time = RequestToken.readInstant(in);
                    int rowIndex = in.readInt();
                    int type = in.readInt();
                    ContentValues data = RequestToken.readContentValues(in);
                    RequestLogRecord req = in.readTypedObject(RequestLogRecord.CREATOR);
                    return new EventLogRecord(time, rowIndex, type, data, req);
                }

                @Override
                public EventLogRecord[] newArray(int size) {
                    return new EventLogRecord[size];
                }
            };
}
