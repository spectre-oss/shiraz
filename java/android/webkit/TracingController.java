// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executor;

public abstract class TracingController {
    private static final TracingController INSTANCE = new DefaultTracingController();

    public TracingController() {
    }

    public static TracingController getInstance() {
        return INSTANCE;
    }

    public abstract void start(TracingConfig tracingConfig);
    public abstract boolean stop(OutputStream outputStream, Executor executor);
    public abstract boolean isTracing();

    private static final class DefaultTracingController extends TracingController {
        private volatile boolean mTracing;

        @Override
        public synchronized void start(TracingConfig tracingConfig) {
            Objects.requireNonNull(tracingConfig, "tracingConfig cannot be null");
            if (mTracing) {
                throw new IllegalStateException("Cannot start tracing: tracing is already enabled");
            }
            mTracing = true;
        }

        @Override
        public synchronized boolean stop(final OutputStream outputStream, Executor executor) {
            Objects.requireNonNull(executor, "executor cannot be null");
            if (!mTracing) {
                return false;
            }
            mTracing = false;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (outputStream != null) {
                        try {
                            outputStream.write("{\"traceEvents\":[]}".getBytes(StandardCharsets.UTF_8));
                            outputStream.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            });
            return true;
        }

        @Override
        public boolean isTracing() {
            return mTracing;
        }
    }
}
