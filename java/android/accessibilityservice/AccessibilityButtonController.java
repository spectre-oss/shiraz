// SPDX-License-Identifier: GPL-3.0-only
package android.accessibilityservice;

import android.os.Handler;
import java.util.IdentityHashMap;
import java.util.Objects;

public final class AccessibilityButtonController {
    private final IdentityHashMap<AccessibilityButtonCallback, Handler> mCallbacks =
            new IdentityHashMap<AccessibilityButtonCallback, Handler>();

    AccessibilityButtonController() {
    }

    public boolean isAccessibilityButtonAvailable() {
        return false;
    }

    public void registerAccessibilityButtonCallback(AccessibilityButtonCallback callback) {
        registerAccessibilityButtonCallback(callback, null);
    }

    public void registerAccessibilityButtonCallback(
            AccessibilityButtonCallback callback, Handler handler) {
        Objects.requireNonNull(callback, "callback");
        synchronized (mCallbacks) {
            mCallbacks.put(callback, handler);
        }
    }

    public void unregisterAccessibilityButtonCallback(AccessibilityButtonCallback callback) {
        Objects.requireNonNull(callback, "callback");
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }

    public abstract static class AccessibilityButtonCallback {
        public AccessibilityButtonCallback() {
        }

        public void onClicked(AccessibilityButtonController controller) {
        }

        public void onAvailabilityChanged(
                AccessibilityButtonController controller, boolean available) {
        }
    }
}
