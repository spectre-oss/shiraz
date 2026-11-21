// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

/** Persistent directory handle for SSL session caching. */
public final class SSLSessionCache {
    final File mDir;

    public SSLSessionCache(File dir) throws IOException {
        Objects.requireNonNull(dir, "dir");
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Unable to create SSLSessionCache directory: " + dir);
        }
        if (!dir.isDirectory()) {
            throw new IOException("Not a directory: " + dir);
        }
        this.mDir = dir;
    }

    public SSLSessionCache(Context context) {
        Objects.requireNonNull(context, "context");
        File dir = context.getDir("sslcache", Context.MODE_PRIVATE);
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        this.mDir = dir;
    }
}
