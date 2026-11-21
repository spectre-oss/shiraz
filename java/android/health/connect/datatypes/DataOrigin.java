// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.util.Objects;

public final class DataOrigin {
    private final String mPackageName;

    DataOrigin(String packageName) {
        mPackageName = packageName != null ? packageName : "";
    }

    public String getPackageName() {
        return mPackageName;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof DataOrigin)) return false;
        return Objects.equals(mPackageName, ((DataOrigin) object).mPackageName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mPackageName);
    }

    public static final class Builder {
        private String mPackageName = "";

        public Builder() {
        }

        public Builder setPackageName(String packageName) {
            Objects.requireNonNull(packageName, "packageName must not be null");
            mPackageName = packageName;
            return this;
        }

        public DataOrigin build() {
            return new DataOrigin(mPackageName);
        }
    }
}
