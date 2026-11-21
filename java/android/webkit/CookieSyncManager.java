// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

import android.content.Context;

@Deprecated
public final class CookieSyncManager implements Runnable {
    private static CookieSyncManager sRef;

    private CookieSyncManager() {
    }

    public static synchronized CookieSyncManager getInstance() {
        if (sRef == null) {
            throw new IllegalStateException(
                    "CookieSyncManager::createInstance() needs to be called before CookieSyncManager::getInstance()");
        }
        return sRef;
    }

    public static synchronized CookieSyncManager createInstance(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Invalid context argument");
        }
        if (sRef == null) {
            sRef = new CookieSyncManager();
        }
        return sRef;
    }

    @Deprecated
    public void sync() {
        CookieManager.getInstance().flush();
    }

    @Deprecated
    protected void syncFromRamToFlash() {
        CookieManager.getInstance().flush();
    }

    @Deprecated
    public void resetSync() {
    }

    @Deprecated
    public void startSync() {
    }

    @Deprecated
    public void stopSync() {
    }

    @Override
    @Deprecated
    public void run() {
        syncFromRamToFlash();
    }
}
