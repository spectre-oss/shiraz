// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch.observer;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class DocumentChangeInfo {
    private final String mPackageName;
    private final String mDatabaseName;
    private final String mNamespace;
    private final String mSchemaName;
    private final Set<String> mChangedDocumentIds;

    public DocumentChangeInfo(
            String packageName,
            String databaseName,
            String namespace,
            String schemaName,
            Set<String> changedDocumentIds) {
        mPackageName = Objects.requireNonNull(packageName);
        mDatabaseName = Objects.requireNonNull(databaseName);
        mNamespace = Objects.requireNonNull(namespace);
        mSchemaName = Objects.requireNonNull(schemaName);
        Objects.requireNonNull(changedDocumentIds);
        mChangedDocumentIds = Collections.unmodifiableSet(
                new LinkedHashSet<String>(changedDocumentIds));
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

    public String getSchemaName() {
        return mSchemaName;
    }

    public Set<String> getChangedDocumentIds() {
        return mChangedDocumentIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentChangeInfo)) return false;
        DocumentChangeInfo that = (DocumentChangeInfo) o;
        return Objects.equals(mPackageName, that.mPackageName)
                && Objects.equals(mDatabaseName, that.mDatabaseName)
                && Objects.equals(mNamespace, that.mNamespace)
                && Objects.equals(mSchemaName, that.mSchemaName)
                && Objects.equals(mChangedDocumentIds, that.mChangedDocumentIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mPackageName, mDatabaseName, mNamespace, mSchemaName, mChangedDocumentIds);
    }

    @Override
    public String toString() {
        return "DocumentChangeInfo{packageName='" + mPackageName
                + "', databaseName='" + mDatabaseName
                + "', namespace='" + mNamespace
                + "', schemaName='" + mSchemaName
                + "', changedDocumentIds=" + mChangedDocumentIds + "}";
    }
}
