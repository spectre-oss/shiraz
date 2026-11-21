// SPDX-License-Identifier: GPL-3.0-only
package android.content.res;

import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.DisplayAdjustments;

/** Resource implementation owned by one open Shiraz resource manager. */
public class ResourcesImpl {
    private final Resources owner;
    private final DisplayAdjustments displayAdjustments;
    private final XmlBlock[] mCachedXmlBlocks = new XmlBlock[4];
    private final int[] mCachedXmlBlockCookies = new int[4];
    private final String[] mCachedXmlBlockFiles = new String[4];
    private final int[] cachedResourceIds = new int[4];
    private int mLastCachedXmlBlockIndex = -1;
    private long cacheGeneration = -1;

    ResourcesImpl(Resources owner) {
        if (owner == null) throw new NullPointerException("resource owner");
        this.owner = owner;
        this.displayAdjustments = new DisplayAdjustments();
    }

    /** Attach another implementation to the same resource state when its configuration matches. */
    public ResourcesImpl(AssetManager assets, DisplayMetrics metrics, Configuration configuration,
                         DisplayAdjustments adjustments) {
        if (assets == null || adjustments == null) throw new NullPointerException("resource implementation arguments");
        owner = assets.shirazResourceOwner();
        DisplayMetrics current = owner.getDisplayMetrics();
        if (metrics == null || configuration == null || !configuration.equals(owner.getConfiguration()) ||
            metrics.widthPixels != current.widthPixels || metrics.heightPixels != current.heightPixels ||
            metrics.densityDpi != current.densityDpi || Float.compare(metrics.density, current.density) != 0 ||
            Float.compare(metrics.scaledDensity, current.scaledDensity) != 0 ||
            Float.compare(metrics.xdpi, current.xdpi) != 0 || Float.compare(metrics.ydpi, current.ydpi) != 0 ||
            adjustments != owner.getImpl().getDisplayAdjustments()) {
            throw new UnsupportedOperationException("Independent resource implementation configuration is not supported");
        }
        displayAdjustments = adjustments;
    }

    public AssetManager getAssets() { return owner.getAssets(); }
    final Resources shirazResourceOwner() { return owner; }
    public DisplayAdjustments getDisplayAdjustments() { return displayAdjustments; }
    DisplayMetrics getDisplayMetrics() { return owner.getDisplayMetrics(); }
    Configuration getConfiguration() { return owner.getConfiguration(); }
    void getValue(int id, TypedValue value, boolean resolveRefs) { owner.getValue(id, value, resolveRefs); }

    XmlResourceParser loadXmlResourceParser(int id) {
        synchronized (owner) { return loadXmlResourceParserLocked(id); }
    }

    private XmlResourceParser loadXmlResourceParserLocked(int id) {
        long generation = owner.shirazResourceGeneration();
        synchronized (mCachedXmlBlocks) {
            if (cacheGeneration != generation) {
                clearXmlCacheLocked();
                cacheGeneration = generation;
            }
        }
        TypedValue value = new TypedValue();
        owner.getValue(id, value, true);
        if (value.type != TypedValue.TYPE_STRING || value.string == null) {
            throw new Resources.NotFoundException("Resource is not a packaged XML file");
        }
        String file = value.string.toString();
        synchronized (mCachedXmlBlocks) {
            for (int i = 0; i < mCachedXmlBlocks.length; ++i) {
                XmlBlock block = mCachedXmlBlocks[i];
                if (block != null && cachedResourceIds[i] == id &&
                    mCachedXmlBlockCookies[i] == value.assetCookie && file.equals(mCachedXmlBlockFiles[i])) {
                    XmlResourceParser cursor = block.newParser();
                    if (cursor != null) return cursor;
                }
            }
        }
        byte[] bytes = owner.shirazReadNonAsset(value.assetCookie, file);
        try {
            XmlBlock block = new XmlBlock(bytes, id);
            XmlResourceParser cursor = block.newParser();
            if (bytes.length > 65536) {
                block.close();
                return cursor;
            }
            synchronized (mCachedXmlBlocks) {
                int index = (mLastCachedXmlBlockIndex + 1) % mCachedXmlBlocks.length;
                XmlBlock old = mCachedXmlBlocks[index];
                mCachedXmlBlocks[index] = block;
                mCachedXmlBlockCookies[index] = value.assetCookie;
                mCachedXmlBlockFiles[index] = file;
                cachedResourceIds[index] = id;
                mLastCachedXmlBlockIndex = index;
                if (old != null) old.close();
            }
            return cursor;
        } catch (RuntimeException failure) {
            throw new Resources.NotFoundException("Cannot parse XML resource #" + Integer.toHexString(id), failure);
        }
    }

    void flushLayoutCache() {
        synchronized (mCachedXmlBlocks) {
            clearXmlCacheLocked();
            cacheGeneration = -1;
        }
    }

    private void clearXmlCacheLocked() {
        for (int i = 0; i < mCachedXmlBlocks.length; ++i) {
            if (mCachedXmlBlocks[i] != null) mCachedXmlBlocks[i].close();
            mCachedXmlBlocks[i] = null;
            mCachedXmlBlockCookies[i] = 0;
            mCachedXmlBlockFiles[i] = null;
            cachedResourceIds[i] = 0;
        }
        mLastCachedXmlBlockIndex = -1;
    }
}
