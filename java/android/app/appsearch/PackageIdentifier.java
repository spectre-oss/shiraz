// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import java.util.Arrays;
import java.util.Objects;

public class PackageIdentifier {
    private final String mPackageName;
    private final byte[] mSha256Certificate;

    public PackageIdentifier(String packageName, byte[] sha256Certificate) {
        mPackageName = Objects.requireNonNull(packageName);
        mSha256Certificate = Objects.requireNonNull(sha256Certificate).clone();
    }

    public String getPackageName() {
        return mPackageName;
    }

    public byte[] getSha256Certificate() {
        return mSha256Certificate.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PackageIdentifier)) return false;
        PackageIdentifier other = (PackageIdentifier) obj;
        return Objects.equals(mPackageName, other.mPackageName)
                && Arrays.equals(mSha256Certificate, other.mSha256Certificate);
    }

    @Override
    public int hashCode() {
        return 31 * mPackageName.hashCode() + Arrays.hashCode(mSha256Certificate);
    }
}
