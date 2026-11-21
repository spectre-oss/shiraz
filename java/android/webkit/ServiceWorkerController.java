// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

public abstract class ServiceWorkerController {
    private static final ServiceWorkerController INSTANCE = new DefaultController();

    public ServiceWorkerController() {
    }

    public static ServiceWorkerController getInstance() {
        return INSTANCE;
    }

    public abstract ServiceWorkerWebSettings getServiceWorkerWebSettings();
    public abstract void setServiceWorkerClient(ServiceWorkerClient client);

    private static final class DefaultSettings extends ServiceWorkerWebSettings {
        private int mCacheMode = WebSettings.LOAD_DEFAULT;
        private boolean mAllowContentAccess = true;
        private boolean mAllowFileAccess = true;
        private boolean mBlockNetworkLoads;

        @Override
        public void setCacheMode(int mode) {
            mCacheMode = mode;
        }

        @Override
        public int getCacheMode() {
            return mCacheMode;
        }

        @Override
        public void setAllowContentAccess(boolean allow) {
            mAllowContentAccess = allow;
        }

        @Override
        public boolean getAllowContentAccess() {
            return mAllowContentAccess;
        }

        @Override
        public void setAllowFileAccess(boolean allow) {
            mAllowFileAccess = allow;
        }

        @Override
        public boolean getAllowFileAccess() {
            return mAllowFileAccess;
        }

        @Override
        public void setBlockNetworkLoads(boolean flag) {
            mBlockNetworkLoads = flag;
        }

        @Override
        public boolean getBlockNetworkLoads() {
            return mBlockNetworkLoads;
        }
    }

    private static final class DefaultController extends ServiceWorkerController {
        private final ServiceWorkerWebSettings mSettings = new DefaultSettings();
        private volatile ServiceWorkerClient mClient;

        @Override
        public ServiceWorkerWebSettings getServiceWorkerWebSettings() {
            return mSettings;
        }

        @Override
        public void setServiceWorkerClient(ServiceWorkerClient client) {
            mClient = client;
        }
    }
}
