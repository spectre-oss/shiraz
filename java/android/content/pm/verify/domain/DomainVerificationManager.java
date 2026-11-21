// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm.verify.domain;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import java.util.Collections;
import java.util.Objects;

/** System service querying Android App Links domain verification state for packages. */
public final class DomainVerificationManager {
    private final Context mContext;

    public DomainVerificationManager() {
        this(null);
    }

    public DomainVerificationManager(Context context) {
        mContext = context;
    }

    public DomainVerificationUserState getDomainVerificationUserState(String packageName)
            throws PackageManager.NameNotFoundException {
        Objects.requireNonNull(packageName, "packageName cannot be null");
        if (packageName.isEmpty()) {
            throw new PackageManager.NameNotFoundException("Empty package name");
        }
        if (mContext != null && mContext.getPackageManager() != null) {
            mContext.getPackageManager().getPackageInfo(packageName, 0);
        }
        return new DomainVerificationUserState(
                packageName,
                Process.myUserHandle(),
                true,
                Collections.<String, Integer>emptyMap());
    }
}
