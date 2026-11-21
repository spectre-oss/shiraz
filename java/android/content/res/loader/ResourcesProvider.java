// SPDX-License-Identifier: GPL-3.0-only
package android.content.res.loader;

import android.content.Context;
import android.content.om.OverlayInfo;
import android.os.ParcelFileDescriptor;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

/** Supplies compiled resource table and asset data to ResourcesLoader instances. */
public class ResourcesProvider implements AutoCloseable, Closeable {
    private final AssetsProvider mAssetsProvider;
    private boolean mOpen = true;
    private int mOpenCount = 0;

    private ResourcesProvider(AssetsProvider assetsProvider) {
        mAssetsProvider = assetsProvider;
    }

    public static ResourcesProvider empty(AssetsProvider assetsProvider) {
        Objects.requireNonNull(assetsProvider, "assetsProvider must not be null");
        return new ResourcesProvider(assetsProvider);
    }

    public static ResourcesProvider loadOverlay(OverlayInfo overlayInfo) throws IOException {
        Objects.requireNonNull(overlayInfo, "overlayInfo must not be null");
        return new ResourcesProvider(null);
    }

    public static ResourcesProvider loadFromApk(ParcelFileDescriptor fileDescriptor)
            throws IOException {
        return loadFromApk(fileDescriptor, null);
    }

    public static ResourcesProvider loadFromApk(
            ParcelFileDescriptor fileDescriptor, AssetsProvider assetsProvider)
            throws IOException {
        Objects.requireNonNull(fileDescriptor, "fileDescriptor must not be null");
        return new ResourcesProvider(assetsProvider);
    }

    public static ResourcesProvider loadFromTable(
            ParcelFileDescriptor fileDescriptor, AssetsProvider assetsProvider)
            throws IOException {
        Objects.requireNonNull(fileDescriptor, "fileDescriptor must not be null");
        return new ResourcesProvider(assetsProvider);
    }

    public static ResourcesProvider loadFromSplit(Context context, String splitName)
            throws IOException {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(splitName, "splitName must not be null");
        throw new IOException("Split not found: " + splitName);
    }

    public static ResourcesProvider loadFromDirectory(
            String path, AssetsProvider assetsProvider) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        File dir = new File(path);
        if (!dir.isDirectory()) {
            throw new IOException("Directory does not exist: " + path);
        }
        return new ResourcesProvider(assetsProvider);
    }

    synchronized void incrementRefCount() {
        if (!mOpen) {
            throw new IllegalStateException("Operation failed: resources provider is closed");
        }
        mOpenCount++;
    }

    synchronized void decrementRefCount() {
        if (mOpenCount > 0) {
            mOpenCount--;
        }
    }

    AssetsProvider getAssetsProvider() {
        return mAssetsProvider;
    }

    @Override
    public synchronized void close() {
        if (!mOpen) {
            return;
        }
        if (mOpenCount != 0) {
            throw new IllegalStateException(
                    "Failed to close provider used by " + mOpenCount + " ResourcesLoader instances");
        }
        mOpen = false;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            synchronized (this) {
                mOpen = false;
            }
        } finally {
            super.finalize();
        }
    }
}
