// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ReportUsageRequest implements Parcelable {
    private final String mNamespace;
    private final String mDocumentId;
    private final long mUsageTimestampMillis;

    private ReportUsageRequest(String namespace, String documentId, long usageTimestampMillis) {
        mNamespace = Objects.requireNonNull(namespace);
        mDocumentId = Objects.requireNonNull(documentId);
        mUsageTimestampMillis = usageTimestampMillis;
    }

    ReportUsageRequest(Parcel in) {
        mNamespace = in.readString();
        mDocumentId = in.readString();
        mUsageTimestampMillis = in.readLong();
    }

    public String getNamespace() {
        return mNamespace;
    }

    public String getDocumentId() {
        return mDocumentId;
    }

    public long getUsageTimestampMillis() {
        return mUsageTimestampMillis;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNamespace);
        dest.writeString(mDocumentId);
        dest.writeLong(mUsageTimestampMillis);
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<ReportUsageRequest> CREATOR =
            new Parcelable.Creator<ReportUsageRequest>() {
                @Override
                public ReportUsageRequest createFromParcel(Parcel in) {
                    return new ReportUsageRequest(in);
                }

                @Override
                public ReportUsageRequest[] newArray(int size) {
                    return new ReportUsageRequest[size];
                }
            };

    public static final class Builder {
        private final String mNamespace;
        private final String mDocumentId;
        private Long mUsageTimestampMillis;

        public Builder(String namespace, String documentId) {
            mNamespace = Objects.requireNonNull(namespace);
            mDocumentId = Objects.requireNonNull(documentId);
        }

        public Builder setUsageTimestampMillis(long usageTimestampMillis) {
            mUsageTimestampMillis = Long.valueOf(usageTimestampMillis);
            return this;
        }

        public ReportUsageRequest build() {
            long ts = mUsageTimestampMillis != null
                    ? mUsageTimestampMillis.longValue()
                    : System.currentTimeMillis();
            return new ReportUsageRequest(mNamespace, mDocumentId, ts);
        }
    }
}
