// SPDX-License-Identifier: GPL-3.0-only
package android.system;

import java.lang.ref.Cleaner;
import java.util.concurrent.ThreadFactory;

/**
 * Provides access to a shared process-wide daemon {@link Cleaner}.
 */
public final class SystemCleaner {
    private static final Cleaner INSTANCE = Cleaner.create(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "SystemCleaner");
            t.setDaemon(true);
            return t;
        }
    });

    private SystemCleaner() {
    }

    public static Cleaner cleaner() {
        return INSTANCE;
    }
}
