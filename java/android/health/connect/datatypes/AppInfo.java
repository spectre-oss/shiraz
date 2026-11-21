// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.graphics.Bitmap;

public final class AppInfo {
    private final String mPackageName;
    private final String mName;
    private final Bitmap mIcon;

    public AppInfo(String packageName, String name, Bitmap icon) {
        mPackageName = packageName;
        mName = name;
        mIcon = icon;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public Bitmap getIcon() {
        return mIcon;
    }

    public String getName() {
        return mName;
    }
}
