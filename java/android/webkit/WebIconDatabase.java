// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

import android.graphics.Bitmap;

@Deprecated
public abstract class WebIconDatabase {
    private static final WebIconDatabase INSTANCE = new DefaultIconDatabase();

    @Deprecated
    public interface IconListener {
        void onReceivedIcon(String url, Bitmap icon);
    }

    public WebIconDatabase() {
    }

    public abstract void open(String path);
    public abstract void close();
    public abstract void removeAllIcons();
    public abstract void requestIconForPageUrl(String url, IconListener listener);
    public abstract void retainIconForPageUrl(String url);
    public abstract void releaseIconForPageUrl(String url);

    public static WebIconDatabase getInstance() {
        return INSTANCE;
    }

    private static final class DefaultIconDatabase extends WebIconDatabase {
        @Override
        public void open(String path) {
        }

        @Override
        public void close() {
        }

        @Override
        public void removeAllIcons() {
        }

        @Override
        public void requestIconForPageUrl(String url, IconListener listener) {
        }

        @Override
        public void retainIconForPageUrl(String url) {
        }

        @Override
        public void releaseIconForPageUrl(String url) {
        }
    }
}
