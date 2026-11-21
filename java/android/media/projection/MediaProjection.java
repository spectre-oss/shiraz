// SPDX-License-Identifier: GPL-3.0-only
package android.media.projection;

import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A token granting applications the ability to capture screen contents and/or record system audio.
 */
public final class MediaProjection {
    public abstract static class Callback {
        public Callback() {
        }

        public void onStop() {
        }

        public void onCapturedContentResize(int width, int height) {
        }

        public void onCapturedContentVisibilityChanged(boolean isVisible) {
        }
    }

    private final Map<Callback, Handler> mCallbacks = new LinkedHashMap<Callback, Handler>();
    private boolean mStopped;

    public MediaProjection() {
    }

    public void registerCallback(Callback callback, Handler handler) {
        Objects.requireNonNull(callback, "callback should not be null");
        Handler target = handler != null ? handler : new Handler(Looper.getMainLooper());
        synchronized (mCallbacks) {
            mCallbacks.put(callback, target);
        }
    }

    public void unregisterCallback(Callback callback) {
        Objects.requireNonNull(callback, "callback should not be null");
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }

    public VirtualDisplay createVirtualDisplay(
            String name,
            int width,
            int height,
            int dpi,
            int flags,
            Surface surface,
            VirtualDisplay.Callback callback,
            Handler handler) {
        Objects.requireNonNull(name, "name must not be null");
        if (width <= 0 || height <= 0 || dpi <= 0) {
            throw new IllegalArgumentException("width, height, and dpi must be > 0");
        }
        if (mStopped) {
            throw new SecurityException("MediaProjection has already been stopped");
        }
        throw new SecurityException("Invalid media projection token: host screen capture is not active");
    }

    public void stop() {
        final Map<Callback, Handler> snapshot;
        synchronized (mCallbacks) {
            if (mStopped) {
                return;
            }
            mStopped = true;
            snapshot = new LinkedHashMap<Callback, Handler>(mCallbacks);
            mCallbacks.clear();
        }
        for (final Map.Entry<Callback, Handler> entry : snapshot.entrySet()) {
            Handler h = entry.getValue();
            final Callback cb = entry.getKey();
            if (h != null) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onStop();
                    }
                });
            } else {
                cb.onStop();
            }
        }
    }
}
