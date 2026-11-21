// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/** Keyguard stubs for desktop hosts without a lock screen. */
public class KeyguardManager {
    private final CopyOnWriteArrayList<KeyguardLockedStateListener> lockedStateListeners =
            new CopyOnWriteArrayList<KeyguardLockedStateListener>();

    @Deprecated
    public class KeyguardLock {
        public void disableKeyguard() {}
        public void reenableKeyguard() {}
    }

    public interface KeyguardLockedStateListener {
        void onKeyguardLockedStateChanged(boolean isKeyguardLocked);
    }

    @Deprecated
    public interface OnKeyguardExitResult {
        void onKeyguardExitResult(boolean success);
    }

    @Deprecated
    public KeyguardLock newKeyguardLock(String tag) {
        return new KeyguardLock();
    }

    public boolean isKeyguardLocked() {
        return false;
    }

    public boolean isKeyguardSecure() {
        return false;
    }

    @Deprecated
    public boolean inKeyguardRestrictedInputMode() {
        return false;
    }

    public boolean isDeviceLocked() {
        return false;
    }

    public boolean isDeviceSecure() {
        return false;
    }

    public boolean isKeyguardEngineered() {
        return false;
    }

    @Deprecated
    public android.content.Intent createConfirmDeviceCredentialIntent(CharSequence title, CharSequence description) {
        return null;
    }

    public void requestDismissKeyguard(Activity activity, KeyguardDismissCallback callback) {
        if (callback != null) {
            callback.onDismissSucceeded();
        }
    }

    @Deprecated
    public void exitKeyguardSecurely(OnKeyguardExitResult callback) {
        if (callback != null) {
            callback.onKeyguardExitResult(true);
        }
    }

    public void addKeyguardLockedStateListener(Executor executor, KeyguardLockedStateListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        lockedStateListeners.addIfAbsent(listener);
    }

    public void removeKeyguardLockedStateListener(KeyguardLockedStateListener listener) {
        Objects.requireNonNull(listener, "listener");
        lockedStateListeners.remove(listener);
    }

    public static abstract class KeyguardDismissCallback {
        public void onDismissError() {}
        public void onDismissSucceeded() {}
        public void onDismissCancelled() {}
    }
}
