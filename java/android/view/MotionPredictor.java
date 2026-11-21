// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.content.Context;

public final class MotionPredictor {
    public MotionPredictor(Context context) {
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }
    }

    public void record(MotionEvent event) {
        if (event == null) {
            throw new NullPointerException("event must not be null");
        }
    }

    public MotionEvent predict(long predictionTimeNanos) {
        return null;
    }

    public boolean isPredictionAvailable(int deviceId, int source) {
        return false;
    }
}
