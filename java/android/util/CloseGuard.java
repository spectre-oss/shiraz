// SPDX-License-Identifier: GPL-3.0-only
package android.util;

/** Tracks explicit resource open/close lifecycles and logs stack traces on leaked resources. */
public final class CloseGuard {
    private volatile Throwable allocationSite;

    public CloseGuard() {}

    public void open(String closer) {
        if (closer == null) {
            throw new NullPointerException("closer == null");
        }
        String message = "Explicit termination method '" + closer + "' not called";
        allocationSite = new Throwable(message);
    }

    public void close() {
        allocationSite = null;
    }

    public void warnIfOpen() {
        Throwable site = allocationSite;
        if (site != null) {
            Log.w(
                    "CloseGuard",
                    "A resource was acquired at attached stack trace but never released. "
                            + "See java.io.Closeable for information on avoiding resource leaks.",
                    site);
        }
    }
}
