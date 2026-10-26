// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.content.res.Configuration;
/** Process component notifications; registration is owned by the Application. */
public interface ComponentCallbacks {
    void onConfigurationChanged(Configuration newConfig);
    void onLowMemory();
}
