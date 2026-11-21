// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

import android.os.Handler;

public abstract class WebMessagePort {
    public abstract static class WebMessageCallback {
        public WebMessageCallback() {
        }

        public void onMessage(WebMessagePort port, WebMessage message) {
        }
    }

    protected WebMessagePort() {
    }

    public abstract void postMessage(WebMessage message);
    public abstract void close();
    public abstract void setWebMessageCallback(WebMessageCallback callback);
    public abstract void setWebMessageCallback(WebMessageCallback callback, Handler handler);
}
