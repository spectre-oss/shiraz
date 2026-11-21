// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SetSchemaRequest {
    public static final int READ_SMS = 1;
    public static final int READ_CALENDAR = 2;
    public static final int READ_CONTACTS = 3;
    public static final int READ_EXTERNAL_STORAGE = 4;
    public static final int READ_HOME_APP_SEARCH_DATA = 5;
    public static final int READ_ASSISTANT_APP_SEARCH_DATA = 6;

    private final Set<AppSearchSchema> mSchemas;
    private final Set<String> mSchemasNotDisplayedBySystem;
    private final Map<String, Set<PackageIdentifier>> mSchemasVisibleToPackages;
    private final Map<String, Set<Set<Integer>>> mSchemasVisibleToPermissions;
    private final Map<String, PackageIdentifier> mPubliclyVisibleSchemas;
    private final Map<String, Set<SchemaVisibilityConfig>> mSchemasVisibleToConfigs;
    private final Map<String, Migrator> mMigrators;
    private final boolean mForceOverride;
    private final int mVersion;

    SetSchemaRequest(Builder b) {
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
        mMigrators = Collections.unmodifiableMap(
                new LinkedHashMap<String, Migrator>(b.mMigrators));
        mForceOverride = b.mForceOverride;
        mVersion = b.mVersion;
    }

    public Set<AppSearchSchema> getSchemas() {
        return mSchemas;
    }

    public Set<String> getSchemasNotDisplayedBySystem() {
        return mSchemasNotDisplayedBySystem;
    }

    public Map<String, Set<PackageIdentifier>> getSchemasVisibleToPackages() {
        return mSchemasVisibleToPackages;
    }

    public Map<String, Set<Set<Integer>>> getRequiredPermissionsForSchemaTypeVisibility() {
        return mSchemasVisibleToPermissions;
    }

    public Map<String, PackageIdentifier> getPubliclyVisibleSchemas() {
        return mPubliclyVisibleSchemas;
    }

    public Map<String, Set<SchemaVisibilityConfig>> getSchemasVisibleToConfigs() {
        return mSchemasVisibleToConfigs;
    }

    public Map<String, Migrator> getMigrators() {
        return mMigrators;
    }

    public boolean isForceOverride() {
        return mForceOverride;
    }

    public int getVersion() {
        return mVersion;
    }

    public static final class Builder {
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
        final Map<String, Migrator> mMigrators = new LinkedHashMap<String, Migrator>();
        boolean mForceOverride = false;
        int mVersion = 1;

        public Builder() {
        }

        public Builder addSchemas(AppSearchSchema... schemas) {
            Objects.requireNonNull(schemas);
            return addSchemas(Arrays.asList(schemas));
        }

        public Builder addSchemas(Collection<AppSearchSchema> schemas) {
            Objects.requireNonNull(schemas);
            for (AppSearchSchema s : schemas) {
                Objects.requireNonNull(s);
                mSchemas.add(s);
            }
            return this;
        }

        public Builder setSchemaTypeDisplayedBySystem(String schemaType, boolean displayed) {
            Objects.requireNonNull(schemaType);
            if (displayed) {
                mSchemasNotDisplayedBySystem.remove(schemaType);
            } else {
                mSchemasNotDisplayedBySystem.add(schemaType);
            }
            return this;
        }

        public Builder addRequiredPermissionsForSchemaTypeVisibility(
                String schemaType, Set<Integer> permissions) {
            Objects.requireNonNull(schemaType);
            Objects.requireNonNull(permissions);
            Set<Set<Integer>> existing = mSchemasVisibleToPermissions.get(schemaType);
            if (existing == null) {
                existing = new LinkedHashSet<Set<Integer>>();
                mSchemasVisibleToPermissions.put(schemaType, existing);
            }
            existing.add(Collections.unmodifiableSet(new LinkedHashSet<Integer>(permissions)));
            return this;
        }

        public Builder clearRequiredPermissionsForSchemaTypeVisibility(String schemaType) {
            Objects.requireNonNull(schemaType);
            mSchemasVisibleToPermissions.remove(schemaType);
            return this;
        }

        public Builder setSchemaTypeVisibilityForPackage(
                String schemaType, boolean visible, PackageIdentifier packageIdentifier) {
            Objects.requireNonNull(schemaType);
            Objects.requireNonNull(packageIdentifier);
            Set<PackageIdentifier> set = mSchemasVisibleToPackages.get(schemaType);
            if (visible) {
                if (set == null) {
                    set = new LinkedHashSet<PackageIdentifier>();
                    mSchemasVisibleToPackages.put(schemaType, set);
                }
                set.add(packageIdentifier);
            } else if (set != null) {
                set.remove(packageIdentifier);
                if (set.isEmpty()) {
                    mSchemasVisibleToPackages.remove(schemaType);
                }
            }
            return this;
        }

        public Builder setPubliclyVisibleSchema(
                String schema, PackageIdentifier packageIdentifier) {
            Objects.requireNonNull(schema);
            if (packageIdentifier == null) {
                mPubliclyVisibleSchemas.remove(schema);
            } else {
                mPubliclyVisibleSchemas.put(schema, packageIdentifier);
            }
            return this;
        }

        public Builder addSchemaTypeVisibleToConfig(
                String schemaType, SchemaVisibilityConfig schemaVisibilityConfig) {
            Objects.requireNonNull(schemaType);
            Objects.requireNonNull(schemaVisibilityConfig);
            Set<SchemaVisibilityConfig> set = mSchemasVisibleToConfigs.get(schemaType);
            if (set == null) {
                set = new LinkedHashSet<SchemaVisibilityConfig>();
                mSchemasVisibleToConfigs.put(schemaType, set);
            }
            set.add(schemaVisibilityConfig);
            return this;
        }

        public Builder clearSchemaTypeVisibleToConfigs(String schemaType) {
            Objects.requireNonNull(schemaType);
            mSchemasVisibleToConfigs.remove(schemaType);
            return this;
        }

        public Builder setMigrator(String schemaType, Migrator migrator) {
            Objects.requireNonNull(schemaType);
            Objects.requireNonNull(migrator);
            mMigrators.put(schemaType, migrator);
            return this;
        }

        public Builder setMigrators(Map<String, Migrator> migrators) {
            Objects.requireNonNull(migrators);
            mMigrators.putAll(migrators);
            return this;
        }

        public Builder setForceOverride(boolean forceOverride) {
            mForceOverride = forceOverride;
            return this;
        }

        public Builder setVersion(int version) {
            if (version < 1) {
                throw new IllegalArgumentException("Version must be >= 1");
            }
            mVersion = version;
            return this;
        }

        public SetSchemaRequest build() {
            return new SetSchemaRequest(this);
        }
    }
}
