// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GetSchemaResponse implements Parcelable {
    private final int mVersion;
    private final Set<AppSearchSchema> mSchemas;
    private final Set<String> mSchemasNotDisplayedBySystem;
    private final Map<String, Set<PackageIdentifier>> mSchemasVisibleToPackages;
    private final Map<String, Set<Set<Integer>>> mSchemasVisibleToPermissions;
    private final Map<String, PackageIdentifier> mPubliclyVisibleSchemas;
    private final Map<String, Set<SchemaVisibilityConfig>> mSchemasVisibleToConfigs;

    GetSchemaResponse(Builder b) {
        mVersion = b.mVersion;
        mSchemas = Collections.unmodifiableSet(new LinkedHashSet<AppSearchSchema>(b.mSchemas));
        mSchemasNotDisplayedBySystem = Collections.unmodifiableSet(
                new LinkedHashSet<String>(b.mSchemasNotDisplayedBySystem));
        mSchemasVisibleToPackages = Collections.unmodifiableMap(
                new LinkedHashMap<String, Set<PackageIdentifier>>(b.mSchemasVisibleToPackages));
        mSchemasVisibleToPermissions = Collections.unmodifiableMap(
                new LinkedHashMap<String, Set<Set<Integer>>>(b.mSchemasVisibleToPermissions));
        mPubliclyVisibleSchemas = Collections.unmodifiableMap(
                new LinkedHashMap<String, PackageIdentifier>(b.mPubliclyVisibleSchemas));
        mSchemasVisibleToConfigs = Collections.unmodifiableMap(
                new LinkedHashMap<String, Set<SchemaVisibilityConfig>>(b.mSchemasVisibleToConfigs));
    }

    GetSchemaResponse(Parcel in) {
        mVersion = in.readInt();
        int schemaCount = in.readInt();
        Set<AppSearchSchema> schemas = new LinkedHashSet<AppSearchSchema>(Math.max(0, schemaCount));
        for (int i = 0; i < schemaCount; i++) {
            schemas.add(AppSearchSchema.CREATOR.createFromParcel(in));
        }
        mSchemas = Collections.unmodifiableSet(schemas);
        int hiddenCount = in.readInt();
        Set<String> hidden = new LinkedHashSet<String>(Math.max(0, hiddenCount));
        for (int i = 0; i < hiddenCount; i++) hidden.add(in.readString());
        mSchemasNotDisplayedBySystem = Collections.unmodifiableSet(hidden);
        mSchemasVisibleToPackages = Collections.emptyMap();
        mSchemasVisibleToPermissions = Collections.emptyMap();
        mPubliclyVisibleSchemas = Collections.emptyMap();
        mSchemasVisibleToConfigs = Collections.emptyMap();
    }

    public int getVersion() {
        return mVersion;
    }

    public Set<AppSearchSchema> getSchemas() {
        return mSchemas;
    }

    public Set<String> getSchemaTypesNotDisplayedBySystem() {
        return mSchemasNotDisplayedBySystem;
    }

    public Map<String, Set<PackageIdentifier>> getSchemaTypesVisibleToPackages() {
        return mSchemasVisibleToPackages;
    }

    public Map<String, Set<Set<Integer>>> getRequiredPermissionsForSchemaTypeVisibility() {
        return mSchemasVisibleToPermissions;
    }

    public Map<String, PackageIdentifier> getPubliclyVisibleSchemas() {
        return mPubliclyVisibleSchemas;
    }

    public Map<String, Set<SchemaVisibilityConfig>> getSchemaTypesVisibleToConfigs() {
        return mSchemasVisibleToConfigs;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mVersion);
        dest.writeInt(mSchemas.size());
        for (AppSearchSchema s : mSchemas) {
            s.writeToParcel(dest, flags);
        }
        dest.writeInt(mSchemasNotDisplayedBySystem.size());
        for (String h : mSchemasNotDisplayedBySystem) {
            dest.writeString(h);
        }
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<GetSchemaResponse> CREATOR =
            new Parcelable.Creator<GetSchemaResponse>() {
                @Override
                public GetSchemaResponse createFromParcel(Parcel in) {
                    return new GetSchemaResponse(in);
                }

                @Override
                public GetSchemaResponse[] newArray(int size) {
                    return new GetSchemaResponse[size];
                }
            };

    public static final class Builder {
        int mVersion = 0;
        final Set<AppSearchSchema> mSchemas = new LinkedHashSet<AppSearchSchema>();
        final Set<String> mSchemasNotDisplayedBySystem = new LinkedHashSet<String>();
        final Map<String, Set<PackageIdentifier>> mSchemasVisibleToPackages =
                new LinkedHashMap<String, Set<PackageIdentifier>>();
        final Map<String, Set<Set<Integer>>> mSchemasVisibleToPermissions =
                new LinkedHashMap<String, Set<Set<Integer>>>();
        final Map<String, PackageIdentifier> mPubliclyVisibleSchemas =
                new LinkedHashMap<String, PackageIdentifier>();
        final Map<String, Set<SchemaVisibilityConfig>> mSchemasVisibleToConfigs =
                new LinkedHashMap<String, Set<SchemaVisibilityConfig>>();

        public Builder() {
        }

        public Builder setVersion(int version) {
            if (version < 0) throw new IllegalArgumentException("Version must be >= 0");
            mVersion = version;
            return this;
        }

        public Builder addSchema(AppSearchSchema schema) {
            Objects.requireNonNull(schema);
            mSchemas.add(schema);
            return this;
        }

        public Builder addSchemaTypeNotDisplayedBySystem(String schemaType) {
            Objects.requireNonNull(schemaType);
            mSchemasNotDisplayedBySystem.add(schemaType);
            return this;
        }

        public Builder setSchemaTypeVisibleToPackages(
                String schemaType, Set<PackageIdentifier> packageIdentifiers) {
            Objects.requireNonNull(schemaType);
            Objects.requireNonNull(packageIdentifiers);
            mSchemasVisibleToPackages.put(
                    schemaType,
                    Collections.unmodifiableSet(
                            new LinkedHashSet<PackageIdentifier>(packageIdentifiers)));
            return this;
        }

        public Builder setRequiredPermissionsForSchemaTypeVisibility(
                String schemaType, Set<Set<Integer>> visibleToPermissionSets) {
            Objects.requireNonNull(schemaType);
            Objects.requireNonNull(visibleToPermissionSets);
            mSchemasVisibleToPermissions.put(
                    schemaType,
                    Collections.unmodifiableSet(
                            new LinkedHashSet<Set<Integer>>(visibleToPermissionSets)));
            return this;
        }

        public Builder setPubliclyVisibleSchema(
                String schemaType, PackageIdentifier packageIdentifier) {
            Objects.requireNonNull(schemaType);
            Objects.requireNonNull(packageIdentifier);
            mPubliclyVisibleSchemas.put(schemaType, packageIdentifier);
            return this;
        }

        public Builder setSchemaTypeVisibleToConfigs(
                String schemaType, Set<SchemaVisibilityConfig> visibleToConfigs) {
            Objects.requireNonNull(schemaType);
            Objects.requireNonNull(visibleToConfigs);
            mSchemasVisibleToConfigs.put(
                    schemaType,
                    Collections.unmodifiableSet(
                            new LinkedHashSet<SchemaVisibilityConfig>(visibleToConfigs)));
            return this;
        }

        public GetSchemaResponse build() {
            return new GetSchemaResponse(this);
        }
    }
}
