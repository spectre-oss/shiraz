// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Offloaded NAT-T / TCP keepalive controller. */
public abstract class SocketKeepalive implements AutoCloseable {
    public static final int ERROR_INVALID_NETWORK = -20;
    public static final int ERROR_INVALID_IP_ADDRESS = -21;
    public static final int ERROR_INVALID_PORT = -22;
    public static final int ERROR_INVALID_LENGTH = -23;
    public static final int ERROR_INVALID_INTERVAL = -24;
    public static final int ERROR_INVALID_SOCKET = -25;
    public static final int ERROR_SOCKET_NOT_IDLE = -26;
    public static final int ERROR_UNSUPPORTED = -30;
    public static final int ERROR_HARDWARE_ERROR = -31;
    public static final int ERROR_INSUFFICIENT_RESOURCES = -32;

    final Executor mExecutor;
    final Callback mCallback;

    SocketKeepalive(Executor executor, Callback callback) {
        this.mExecutor = Objects.requireNonNull(executor, "executor");
        this.mCallback = Objects.requireNonNull(callback, "callback");
    }

    public static class Callback {
        public Callback() {
        }

        public void onStarted() {
        }

        public void onStopped() {
        }

        public void onError(int error) {
        }

        public void onDataReceived() {
        }
    }

    public final void start(final int intervalSec) {
        if (intervalSec < 10 || intervalSec > 3600) {
            mExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            mCallback.onError(ERROR_INVALID_INTERVAL);
                        }
                    });
            return;
        }
        startImpl(intervalSec);
    }

    public final void stop() {
        stopImpl();
    }

    @Override
    public final void close() {
        stopImpl();
    }

    abstract void startImpl(int intervalSec);

    abstract void stopImpl();
}
