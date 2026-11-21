// SPDX-License-Identifier: GPL-3.0-only
package android.drm;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

/** DRM registration or rights acquisition buffer with arbitrary key/value attributes. */
@Deprecated
public class DrmInfo {
    private final byte[] mData;
    private final String mMimeType;
    private final int mInfoType;
    private final HashMap<String, Object> mAttributes = new HashMap<String, Object>();

    public DrmInfo(int infoType, byte[] data, String mimeType) {
        this.mInfoType = infoType;
        this.mMimeType = mimeType;
        this.mData = data;
        if (!isValid()) {
            throw new IllegalArgumentException(
                    "infoType: "
                            + infoType
                            + ", mimeType: "
                            + mimeType
                            + ", data: "
                            + Arrays.toString(data));
        }
    }

    public DrmInfo(int infoType, String path, String mimeType) {
        this.mInfoType = infoType;
        this.mMimeType = mimeType;
        byte[] read;
        try {
            read = DrmUtils.readBytes(path);
        } catch (IOException e) {
            read = null;
        }
        this.mData = read;
        if (!isValid()) {
            throw new IllegalArgumentException(
                    "infoType: "
                            + infoType
                            + ", mimeType: "
                            + mimeType
                            + ", path: "
                            + path);
        }
    }

    public void put(String key, Object value) {
        mAttributes.put(key, value);
    }

    public Object get(String key) {
        return mAttributes.get(key);
    }

    public Iterator<String> keyIterator() {
        return mAttributes.keySet().iterator();
    }

    public Iterator<Object> iterator() {
        return mAttributes.values().iterator();
    }

    public byte[] getData() {
        return mData;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public int getInfoType() {
        return mInfoType;
    }

    boolean isValid() {
        return mMimeType != null
                && !mMimeType.isEmpty()
                && mData != null
                && mData.length > 0
                && DrmInfoRequest.isValidType(mInfoType);
    }
}
