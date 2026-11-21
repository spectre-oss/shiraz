// SPDX-License-Identifier: GPL-3.0-only
package android.app.slice;

import android.content.Context;
import android.net.Uri;
import java.util.Objects;

/** Metrics logger for Slice visibility and interaction events. */
public class SliceMetrics {
    private final Context mContext;
    private final Uri mUri;
    private boolean mVisible;

    public SliceMetrics(Context context, Uri uri) {
        this.mContext = Objects.requireNonNull(context, "context");
        this.mUri = Objects.requireNonNull(uri, "uri");
    }

    public synchronized void logVisible() {
        mVisible = true;
    }

    public synchronized void logHidden() {
        mVisible = false;
    }

    public synchronized void logTouch(int actionType, Uri subSlice) {
        Objects.requireNonNull(subSlice, "subSlice");
    }
}
