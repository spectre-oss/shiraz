// SPDX-License-Identifier: GPL-3.0-only
package android.database;

import android.os.Handler;

/** Observer for content URI changes; delivery is host-mediated. */
public abstract class ContentObserver {
    private final Handler handler;

    public ContentObserver(Handler handler) { this.handler = handler; }

    public boolean deliverSelfNotifications() { return false; }

    public final void dispatchChange(boolean selfChange) { dispatchChange(selfChange, null); }

    public final void dispatchChange(boolean selfChange, android.net.Uri uri) {
        if (handler == null) onChange(selfChange, uri);
        else handler.post(new Runnable() {
            public void run() { onChange(selfChange, uri); }
        });
    }

    public void onChange(boolean selfChange) { }

    public void onChange(boolean selfChange, android.net.Uri uri) { onChange(selfChange); }

    public void onChange(boolean selfChange, android.net.Uri uri, int flags) { onChange(selfChange, uri); }
}
