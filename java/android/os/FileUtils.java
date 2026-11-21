// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.Executor;
import shiraz.runtime.NativeFd;

/** Stream and file descriptor copy and cleanup utilities. */
public final class FileUtils {
    public interface ProgressListener {
        void onProgress(long progress);
    }

    private static final long PROGRESS_INTERVAL_MS = 500L;

    private FileUtils() {
    }

    public static long copy(InputStream in, OutputStream out) throws IOException {
        return copy(in, out, null, null, null);
    }

    public static long copy(
            InputStream in,
            OutputStream out,
            CancellationSignal signal,
            Executor executor,
            final ProgressListener listener)
            throws IOException {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        byte[] buffer = new byte[8192];
        long total = 0L;
        long checkpoint = 0L;
        long lastProgressTime = SystemClock.uptimeMillis();
        int read;
        while (true) {
            if (signal != null) {
                signal.throwIfCanceled();
            }
            read = in.read(buffer);
            if (read < 0) {
                break;
            }
            out.write(buffer, 0, read);
            total += read;
            checkpoint += read;
            if (listener != null) {
                long now = SystemClock.uptimeMillis();
                if (now - lastProgressTime >= PROGRESS_INTERVAL_MS && checkpoint > 0) {
                    lastProgressTime = now;
                    checkpoint = 0L;
                    dispatchProgress(executor, listener, total);
                }
            }
        }
        if (listener != null) {
            dispatchProgress(executor, listener, total);
        }
        return total;
    }

    public static long copy(FileDescriptor in, FileDescriptor out) throws IOException {
        return copy(in, out, null, null, null);
    }

    public static long copy(
            FileDescriptor in,
            FileDescriptor out,
            CancellationSignal signal,
            Executor executor,
            ProgressListener listener)
            throws IOException {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        ParcelFileDescriptor inDup = ParcelFileDescriptor.dup(in);
        ParcelFileDescriptor outDup = ParcelFileDescriptor.dup(out);
        try {
            FileInputStream fis = new FileInputStream(inDup.getFileDescriptor());
            FileOutputStream fos = new FileOutputStream(outDup.getFileDescriptor());
            long copied = copy(fis, fos, signal, executor, listener);
            fos.flush();
            return copied;
        } finally {
            closeQuietly(inDup);
            closeQuietly(outDup);
        }
    }

    private static void dispatchProgress(
            Executor executor, final ProgressListener listener, final long progress) {
        if (executor != null) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    listener.onProgress(progress);
                }
            });
        } else {
            listener.onProgress(progress);
        }
    }

    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    public static void closeQuietly(FileDescriptor fd) {
        if (fd != null) {
            try {
                NativeFd.close(fd);
            } catch (Throwable ignored) {
            }
        }
    }
}
