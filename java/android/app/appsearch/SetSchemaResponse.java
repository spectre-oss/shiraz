// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SetSchemaResponse implements Parcelable {
    private final List<MigrationFailure> mMigrationFailures;
    private final Set<String> mDeletedTypes;
    private final Set<String> mMigratedTypes;
    private final Set<String> mIncompatibleTypes;

    private SetSchemaResponse(
            List<MigrationFailure> migrationFailures,
            Set<String> deletedTypes,
            Set<String> migratedTypes,
            Set<String> incompatibleTypes) {
        mMigrationFailures = Collections.unmodifiableList(
                new ArrayList<MigrationFailure>(migrationFailures));
        mDeletedTypes = Collections.unmodifiableSet(new LinkedHashSet<String>(deletedTypes));
        mMigratedTypes = Collections.unmodifiableSet(new LinkedHashSet<String>(migratedTypes));
        mIncompatibleTypes = Collections.unmodifiableSet(
                new LinkedHashSet<String>(incompatibleTypes));
    }

    SetSchemaResponse(Parcel in) {
        int failCount = in.readInt();
        List<MigrationFailure> fails = new ArrayList<MigrationFailure>(Math.max(0, failCount));
        for (int i = 0; i < failCount; i++) {
            fails.add(MigrationFailure.CREATOR.createFromParcel(in));
        }
        mMigrationFailures = Collections.unmodifiableList(fails);
        mDeletedTypes = readStringSet(in);
        mMigratedTypes = readStringSet(in);
        mIncompatibleTypes = readStringSet(in);
    }

    private static Set<String> readStringSet(Parcel in) {
        int count = in.readInt();
        Set<String> set = new LinkedHashSet<String>(Math.max(0, count));
        for (int i = 0; i < count; i++) set.add(in.readString());
        return Collections.unmodifiableSet(set);
    }

    private static void writeStringSet(Parcel dest, Set<String> set) {
        dest.writeInt(set.size());
        for (String s : set) dest.writeString(s);
    }

    public List<MigrationFailure> getMigrationFailures() {
        return mMigrationFailures;
    }

    public Set<String> getDeletedTypes() {
        return mDeletedTypes;
    }

    public Set<String> getMigratedTypes() {
        return mMigratedTypes;
    }

    public Set<String> getIncompatibleTypes() {
        return mIncompatibleTypes;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mMigrationFailures.size());
        for (MigrationFailure mf : mMigrationFailures) {
            mf.writeToParcel(dest, flags);
        }
        writeStringSet(dest, mDeletedTypes);
        writeStringSet(dest, mMigratedTypes);
        writeStringSet(dest, mIncompatibleTypes);
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<SetSchemaResponse> CREATOR =
            new Parcelable.Creator<SetSchemaResponse>() {
                @Override
                public SetSchemaResponse createFromParcel(Parcel in) {
                    return new SetSchemaResponse(in);
                }

                @Override
                public SetSchemaResponse[] newArray(int size) {
                    return new SetSchemaResponse[size];
                }
            };

    public static final class Builder {
        private final List<MigrationFailure> mMigrationFailures =
                new ArrayList<MigrationFailure>();
        private final Set<String> mDeletedTypes = new LinkedHashSet<String>();
        private final Set<String> mMigratedTypes = new LinkedHashSet<String>();
        private final Set<String> mIncompatibleTypes = new LinkedHashSet<String>();

        public Builder() {
        }

        public Builder addMigrationFailures(Collection<MigrationFailure> migrationFailures) {
            Objects.requireNonNull(migrationFailures);
            for (MigrationFailure mf : migrationFailures) {
                addMigrationFailure(mf);
            }
            return this;
        }

        public Builder addMigrationFailure(MigrationFailure migrationFailure) {
            Objects.requireNonNull(migrationFailure);
            mMigrationFailures.add(migrationFailure);
            return this;
        }

        public Builder addDeletedTypes(Collection<String> deletedTypes) {
            Objects.requireNonNull(deletedTypes);
            mDeletedTypes.addAll(deletedTypes);
            return this;
        }

        public Builder addDeletedType(String deletedType) {
            Objects.requireNonNull(deletedType);
            mDeletedTypes.add(deletedType);
            return this;
        }

        public Builder addIncompatibleTypes(Collection<String> incompatibleTypes) {
            Objects.requireNonNull(incompatibleTypes);
            mIncompatibleTypes.addAll(incompatibleTypes);
            return this;
        }

        public Builder addIncompatibleType(String incompatibleType) {
            Objects.requireNonNull(incompatibleType);
            mIncompatibleTypes.add(incompatibleType);
            return this;
        }

        public Builder addMigratedTypes(Collection<String> migratedTypes) {
            Objects.requireNonNull(migratedTypes);
            mMigratedTypes.addAll(migratedTypes);
            return this;
        }

        public Builder addMigratedType(String migratedType) {
            Objects.requireNonNull(migratedType);
            mMigratedTypes.add(migratedType);
            return this;
        }

        public SetSchemaResponse build() {
            return new SetSchemaResponse(
                    mMigrationFailures, mDeletedTypes, mMigratedTypes, mIncompatibleTypes);
        }
    }

    public static class MigrationFailure implements Parcelable {
        private final String mNamespace;
        private final String mDocumentId;
        private final String mSchemaType;
        private final AppSearchResult<Void> mFailedResult;

        public MigrationFailure(
                String namespace,
                String documentId,
                String schemaType,
                AppSearchResult<?> failedResult) {
            mNamespace = Objects.requireNonNull(namespace);
            mDocumentId = Objects.requireNonNull(documentId);
            mSchemaType = Objects.requireNonNull(schemaType);
            Objects.requireNonNull(failedResult);
            if (failedResult.isSuccess()) {
                throw new IllegalArgumentException("failedResult was actually successful");
            }
            mFailedResult = AppSearchResult.newFailedResult(
                    failedResult.getResultCode(), failedResult.getErrorMessage());
        }

        public String getNamespace() {
            return mNamespace;
        }

        public String getDocumentId() {
            return mDocumentId;
        }

        public String getSchemaType() {
            return mSchemaType;
        }

        public AppSearchResult<Void> getAppSearchResult() {
            return mFailedResult;
        }

        @Override
        public String toString() {
            return "MigrationFailure { schemaType: " + mSchemaType
                    + ", namespace: " + mNamespace
                    + ", documentId: " + mDocumentId
                    + ", appSearchResult: " + mFailedResult + "}";
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mNamespace);
            dest.writeString(mDocumentId);
            dest.writeString(mSchemaType);
            dest.writeInt(mFailedResult.getResultCode());
            dest.writeString(mFailedResult.getErrorMessage());
        }

        @Override
        public final int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<MigrationFailure> CREATOR =
                new Parcelable.Creator<MigrationFailure>() {
                    @Override
                    public MigrationFailure createFromParcel(Parcel in) {
                        String ns = in.readString();
                        String id = in.readString();
                        String schema = in.readString();
                        int code = in.readInt();
                        String msg = in.readString();
                        return new MigrationFailure(
                                ns, id, schema, AppSearchResult.newFailedResult(code, msg));
                    }

                    @Override
                    public MigrationFailure[] newArray(int size) {
                        return new MigrationFailure[size];
                    }
                };
    }
}
