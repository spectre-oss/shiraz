// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.pm.PackageManager;
import android.net.Uri;
import java.util.Objects;

public final class ComponentCaller {
    private final int mUid;
    private final String mPackage;

    ComponentCaller(int uid, String pkg) {
        mUid = uid;
        mPackage = pkg;
    }

    public int getUid() {
        return mUid;
    }

    public String getPackage() {
        return mPackage;
    }

    public int checkContentUriPermission(Uri uri, int modeFlags) {
        Objects.requireNonNull(uri, "uri");
        return PackageManager.PERMISSION_DENIED;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ComponentCaller)) {
            return false;
        }
        ComponentCaller other = (ComponentCaller) obj;
        return mUid == other.mUid && Objects.equals(mPackage, other.mPackage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mUid), mPackage);
    }
}
