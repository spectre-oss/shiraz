// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

import android.content.Context;
import java.util.HashMap;
import java.util.Map;

public abstract class WebViewDatabase {
    private static final WebViewDatabase INSTANCE = new DefaultWebViewDatabase();

    public WebViewDatabase() {
    }

    public static WebViewDatabase getInstance(Context context) {
        return INSTANCE;
    }

    @Deprecated
    public abstract boolean hasUsernamePassword();

    @Deprecated
    public abstract void clearUsernamePassword();

    public abstract boolean hasHttpAuthUsernamePassword();
    public abstract void clearHttpAuthUsernamePassword();
    public abstract void setHttpAuthUsernamePassword(String host, String realm,
            String username, String password);
    public abstract String[] getHttpAuthUsernamePassword(String host, String realm);

    @Deprecated
    public abstract boolean hasFormData();

    @Deprecated
    public abstract void clearFormData();

    private static final class DefaultWebViewDatabase extends WebViewDatabase {
        private final Map<String, String[]> mHttpAuth = new HashMap<>();

        @Override
        public boolean hasUsernamePassword() {
            return false;
        }

        @Override
        public void clearUsernamePassword() {
        }

        @Override
        public synchronized boolean hasHttpAuthUsernamePassword() {
            return !mHttpAuth.isEmpty();
        }

        @Override
        public synchronized void clearHttpAuthUsernamePassword() {
            mHttpAuth.clear();
        }

        @Override
        public synchronized void setHttpAuthUsernamePassword(String host, String realm,
                String username, String password) {
            if (host == null) return;
            String key = host + "\0" + (realm != null ? realm : "");
            if (username == null && password == null) {
                mHttpAuth.remove(key);
            } else {
                mHttpAuth.put(key, new String[] { username, password });
            }
        }

        @Override
        public synchronized String[] getHttpAuthUsernamePassword(String host, String realm) {
            if (host == null) return null;
            String key = host + "\0" + (realm != null ? realm : "");
            String[] creds = mHttpAuth.get(key);
            return creds != null ? creds.clone() : null;
        }

        @Override
        public boolean hasFormData() {
            return false;
        }

        @Override
        public void clearFormData() {
        }
    }
}
