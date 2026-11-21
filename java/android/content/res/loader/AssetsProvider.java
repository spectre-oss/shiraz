// SPDX-License-Identifier: GPL-3.0-only
package android.content.res.loader;

import android.content.res.AssetFileDescriptor;

/** Callback interface supplying custom file-backed assets to a ResourcesProvider. */
public interface AssetsProvider {
    default AssetFileDescriptor loadAssetFd(String path, int accessMode) {
        return null;
    }
}
