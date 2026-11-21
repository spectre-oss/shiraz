// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import java.util.Objects;

public final class ReportSystemUsageRequest {
    private final String mPackageName;
    private final String mDatabaseName;
    private final String mNamespace;
    private final String mDocumentId;
    private final long mUsageTimestampMillis;

    private ReportSystemUsageRequest(
            String packageName,
            String databaseName,
            String namespace,
            String documentId,
            long usageTimestampMillis) {
        mPackageName = Objects.requireNonNull(packageName);
        mDatabaseName = Objects.requireNonNull(databaseName);
        mNamespace = Objects.requireNonNull(namespace);
        mDocumentId = Objects.requireNonNull(documentId);
        mUsageTimestampMillis = usageTimestampMillis;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getDatabaseName() {
        return mDatabaseName;
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

    public static final class Builder {
        private final String mPackageName;
        private final String mDatabaseName;
        private final String mNamespace;
        private final String mDocumentId;
        private Long mUsageTimestampMillis;

        public Builder(
                String packageName, String databaseName, String namespace, String documentId) {
            mPackageName = Objects.requireNonNull(packageName);
            mDatabaseName = Objects.requireNonNull(databaseName);
            mNamespace = Objects.requireNonNull(namespace);
            mDocumentId = Objects.requireNonNull(documentId);
        }

        public Builder setUsageTimestampMillis(long usageTimestampMillis) {
            mUsageTimestampMillis = Long.valueOf(usageTimestampMillis);
            return this;
        }

        public ReportSystemUsageRequest build() {
            long ts = mUsageTimestampMillis != null
                    ? mUsageTimestampMillis.longValue()
                    : System.currentTimeMillis();
            return new ReportSystemUsageRequest(
                    mPackageName, mDatabaseName, mNamespace, mDocumentId, ts);
        }
    }
}
