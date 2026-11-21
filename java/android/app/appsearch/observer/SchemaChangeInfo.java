// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch.observer;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class SchemaChangeInfo {
    private final String mPackageName;
    private final String mDatabaseName;
    private final Set<String> mChangedSchemaNames;

    public SchemaChangeInfo(
            String packageName, String databaseName, Set<String> changedSchemaNames) {
        mPackageName = Objects.requireNonNull(packageName);
        mDatabaseName = Objects.requireNonNull(databaseName);
        Objects.requireNonNull(changedSchemaNames);
        mChangedSchemaNames = Collections.unmodifiableSet(
                new LinkedHashSet<String>(changedSchemaNames));
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getDatabaseName() {
        return mDatabaseName;
    }

    public Set<String> getChangedSchemaNames() {
        return mChangedSchemaNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchemaChangeInfo)) return false;
        SchemaChangeInfo that = (SchemaChangeInfo) o;
        return Objects.equals(mPackageName, that.mPackageName)
                && Objects.equals(mDatabaseName, that.mDatabaseName)
                && Objects.equals(mChangedSchemaNames, that.mChangedSchemaNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPackageName, mDatabaseName, mChangedSchemaNames);
    }

    @Override
    public String toString() {
        return "SchemaChangeInfo{packageName='" + mPackageName
                + "', databaseName='" + mDatabaseName
                + "', changedSchemaNames=" + mChangedSchemaNames + "}";
    }
}
