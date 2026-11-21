// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.pm.ApplicationInfo;

public interface ZygotePreload {
    void doPreload(ApplicationInfo appInfo);
}
