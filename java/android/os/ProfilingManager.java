// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Application profiling request and result listener manager. */
public final class ProfilingManager {
    public static final int PROFILING_TYPE_JAVA_HEAP_DUMP = 1;
    public static final int PROFILING_TYPE_HEAP_PROFILE = 2;
    public static final int PROFILING_TYPE_STACK_SAMPLING = 3;
    public static final int PROFILING_TYPE_SYSTEM_TRACE = 4;

    private static final class ListenerEntry {
        final Executor executor;
        final Consumer<ProfilingResult> listener;

        ListenerEntry(Executor executor, Consumer<ProfilingResult> listener) {
            this.executor = executor;
            this.listener = listener;
        }
    }

    private final List<ListenerEntry> globalListeners = new ArrayList<ListenerEntry>();

    public ProfilingManager() {
    }

    public void requestProfiling(
            int profilingType,
            Bundle parameters,
            String tag,
            CancellationSignal cancellationSignal,
            Executor executor,
            final Consumer<ProfilingResult> listener) {
        if (cancellationSignal != null && cancellationSignal.isCanceled()) {
            return;
        }
        final ProfilingResult result;
        if (profilingType < PROFILING_TYPE_JAVA_HEAP_DUMP
                || profilingType > PROFILING_TYPE_SYSTEM_TRACE) {
            result = new ProfilingResult(
                    ProfilingResult.ERROR_FAILED_INVALID_REQUEST,
                    null,
                    tag,
                    "Invalid profiling type: " + profilingType);
        } else if (profilingType == PROFILING_TYPE_JAVA_HEAP_DUMP) {
            ProfilingResult r;
            try {
                File out = File.createTempFile("shiraz_profile_", ".hprof");
                Debug.dumpHprofData(out.getAbsolutePath());
                r = new ProfilingResult(
                        ProfilingResult.ERROR_NONE, out.getAbsolutePath(), tag, null);
            } catch (Exception e) {
                r = new ProfilingResult(
                        ProfilingResult.ERROR_FAILED_EXECUTING, null, tag, e.getMessage());
            }
            result = r;
        } else {
            result = new ProfilingResult(
                    ProfilingResult.ERROR_FAILED_EXECUTING,
                    null,
                    tag,
                    "Perfetto kernel tracing is unavailable on desktop host");
        }

        if (executor != null && listener != null) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    listener.accept(result);
                }
            });
        }
        List<ListenerEntry> snapshot;
        synchronized (globalListeners) {
            snapshot = new ArrayList<ListenerEntry>(globalListeners);
        }
        for (int i = 0; i < snapshot.size(); i++) {
            final ListenerEntry entry = snapshot.get(i);
            entry.executor.execute(new Runnable() {
                @Override
                public void run() {
                    entry.listener.accept(result);
                }
            });
        }
    }

    public void registerForAllProfilingResults(
            Executor executor, Consumer<ProfilingResult> listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        synchronized (globalListeners) {
            globalListeners.add(new ListenerEntry(executor, listener));
        }
    }

    public void unregisterForAllProfilingResults(Consumer<ProfilingResult> listener) {
        synchronized (globalListeners) {
            if (listener == null) {
                globalListeners.clear();
                return;
            }
            for (int i = globalListeners.size() - 1; i >= 0; i--) {
                if (globalListeners.get(i).listener == listener) {
                    globalListeners.remove(i);
                }
            }
        }
    }
}
