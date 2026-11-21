// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

public class ConnectionMigrationOptions {
    public static final int MIGRATION_OPTION_UNSPECIFIED = 0;
    public static final int MIGRATION_OPTION_ENABLED = 1;
    public static final int MIGRATION_OPTION_DISABLED = 2;

    private final int mDefaultNetworkMigration;
    private final int mPathDegradationMigration;
    private final int mAllowNonDefaultNetworkUsage;

    ConnectionMigrationOptions(Builder builder) {
        this.mDefaultNetworkMigration = builder.mDefaultNetworkMigration;
        this.mPathDegradationMigration = builder.mPathDegradationMigration;
        this.mAllowNonDefaultNetworkUsage = builder.mAllowNonDefaultNetworkUsage;
    }

    public int getDefaultNetworkMigration() {
        return mDefaultNetworkMigration;
    }

    public int getPathDegradationMigration() {
        return mPathDegradationMigration;
    }

    public int getAllowNonDefaultNetworkUsage() {
        return mAllowNonDefaultNetworkUsage;
    }

    public static final class Builder {
        int mDefaultNetworkMigration = MIGRATION_OPTION_UNSPECIFIED;
        int mPathDegradationMigration = MIGRATION_OPTION_UNSPECIFIED;
        int mAllowNonDefaultNetworkUsage = MIGRATION_OPTION_UNSPECIFIED;

        public Builder() {}

        public Builder setDefaultNetworkMigration(int option) {
            this.mDefaultNetworkMigration = option;
            return this;
        }

        public Builder setPathDegradationMigration(int option) {
            this.mPathDegradationMigration = option;
            return this;
        }

        public Builder setAllowNonDefaultNetworkUsage(int option) {
            this.mAllowNonDefaultNetworkUsage = option;
            return this;
        }

        public ConnectionMigrationOptions build() {
            return new ConnectionMigrationOptions(this);
        }
    }
}
