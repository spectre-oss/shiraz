// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import android.os.Bundle;

public abstract class Event {
    long mTimeSinceCreatedMillis = -1L;
    Bundle mMetricsBundle = new Bundle();

    Event() {
    }

    Event(long timeSinceCreatedMillis, Bundle extras) {
        mTimeSinceCreatedMillis = timeSinceCreatedMillis;
        mMetricsBundle = extras != null ? new Bundle(extras) : new Bundle();
    }

    public long getTimeSinceCreatedMillis() {
        return mTimeSinceCreatedMillis;
    }

    public Bundle getMetricsBundle() {
        return new Bundle(mMetricsBundle);
    }
}
