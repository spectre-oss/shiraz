// SPDX-License-Identifier: GPL-3.0-only
package android.accessibilityservice;

import android.os.Handler;
import java.util.IdentityHashMap;
import java.util.Objects;

public final class FingerprintGestureController {
    public static final int FINGERPRINT_GESTURE_SWIPE_RIGHT = 0x00000001;
    public static final int FINGERPRINT_GESTURE_SWIPE_LEFT = 0x00000002;
    public static final int FINGERPRINT_GESTURE_SWIPE_UP = 0x00000004;
    public static final int FINGERPRINT_GESTURE_SWIPE_DOWN = 0x00000008;

    private final IdentityHashMap<FingerprintGestureCallback, Handler> mCallbacks =
            new IdentityHashMap<FingerprintGestureCallback, Handler>();

    FingerprintGestureController() {
    }

    public boolean isGestureDetectionAvailable() {
        return false;
    }

    public void registerFingerprintGestureCallback(
            FingerprintGestureCallback callback, Handler handler) {
        Objects.requireNonNull(callback, "callback");
        synchronized (mCallbacks) {
            mCallbacks.put(callback, handler);
        }
    }

    public void unregisterFingerprintGestureCallback(FingerprintGestureCallback callback) {
        if (callback == null) {
            return;
        }
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }

    public abstract static class FingerprintGestureCallback {
        public FingerprintGestureCallback() {
        }

        public void onGestureDetectionAvailabilityChanged(boolean available) {
        }

        public void onGestureDetected(int gesture) {
        }
    }
}
