// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.graphics.Bitmap;
import android.os.Bundle;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Deprecated
public abstract class MediaMetadataEditor {
    public static final int BITMAP_KEY_ARTWORK = 100;
    public static final int RATING_KEY_BY_OTHERS = 101;
    public static final int RATING_KEY_BY_USER = 0x10000001;

    protected long mEditableKeys;
    protected boolean mMetadataChanged;
    protected boolean mApplied;
    protected boolean mArtworkChanged;
    protected Bitmap mEditorArtwork;
    protected Bundle mEditorMetadata = new Bundle();

    private final Set<Integer> mEditableKeySet = new LinkedHashSet<Integer>();
    private final Map<Integer, Object> mValues = new LinkedHashMap<Integer, Object>();

    protected MediaMetadataEditor() {}

    public abstract void apply();

    public synchronized void clear() {
        if (mApplied) return;
        mEditorMetadata.clear();
        mEditorArtwork = null;
        mValues.clear();
        mMetadataChanged = true;
        mArtworkChanged = true;
    }

    public synchronized void addEditableKey(int key) {
        if (mApplied) return;
        mEditableKeySet.add(Integer.valueOf(key));
        mEditableKeys |= (key & 0x1fffffffL);
        mMetadataChanged = true;
    }

    public synchronized void removeEditableKeys() {
        if (mApplied) return;
        mEditableKeySet.clear();
        mEditableKeys = 0L;
        mMetadataChanged = true;
    }

    public synchronized int[] getEditableKeys() {
        if (mEditableKeySet.isEmpty()) return null;
        int[] res = new int[mEditableKeySet.size()];
        int i = 0;
        for (Integer k : mEditableKeySet) {
            res[i++] = k.intValue();
        }
        return res;
    }

    public synchronized MediaMetadataEditor putString(int key, String value)
            throws IllegalArgumentException {
        if (mApplied) return this;
        mValues.put(Integer.valueOf(key), value);
        mEditorMetadata.putString(String.valueOf(key), value);
        mMetadataChanged = true;
        return this;
    }

    public synchronized MediaMetadataEditor putLong(int key, long value)
            throws IllegalArgumentException {
        if (mApplied) return this;
        mValues.put(Integer.valueOf(key), Long.valueOf(value));
        mEditorMetadata.putLong(String.valueOf(key), value);
        mMetadataChanged = true;
        return this;
    }

    public synchronized MediaMetadataEditor putBitmap(int key, Bitmap bitmap)
            throws IllegalArgumentException {
        if (mApplied) return this;
        if (key != BITMAP_KEY_ARTWORK) {
            throw new IllegalArgumentException("Invalid type 'Bitmap' for key " + key);
        }
        mEditorArtwork = bitmap;
        mValues.put(Integer.valueOf(key), bitmap);
        mArtworkChanged = true;
        return this;
    }

    public synchronized MediaMetadataEditor putObject(int key, Object value)
            throws IllegalArgumentException {
        if (mApplied) return this;
        if (value instanceof Long) {
            return putLong(key, ((Long) value).longValue());
        } else if (value instanceof String) {
            return putString(key, (String) value);
        } else if (key == BITMAP_KEY_ARTWORK && (value == null || value instanceof Bitmap)) {
            return putBitmap(key, (Bitmap) value);
        }
        mValues.put(Integer.valueOf(key), value);
        mMetadataChanged = true;
        return this;
    }

    public synchronized long getLong(int key, long defaultValue)
            throws IllegalArgumentException {
        Object val = mValues.get(Integer.valueOf(key));
        return val instanceof Long ? ((Long) val).longValue() : defaultValue;
    }

    public synchronized String getString(int key, String defaultValue)
            throws IllegalArgumentException {
        Object val = mValues.get(Integer.valueOf(key));
        return val instanceof String ? (String) val : defaultValue;
    }

    public synchronized Bitmap getBitmap(int key, Bitmap defaultValue)
            throws IllegalArgumentException {
        if (key != BITMAP_KEY_ARTWORK) {
            throw new IllegalArgumentException("Invalid type 'Bitmap' for key " + key);
        }
        return mEditorArtwork != null ? mEditorArtwork : defaultValue;
    }

    public synchronized Object getObject(int key, Object defaultValue)
            throws IllegalArgumentException {
        return mValues.containsKey(Integer.valueOf(key))
                ? mValues.get(Integer.valueOf(key)) : defaultValue;
    }
}
