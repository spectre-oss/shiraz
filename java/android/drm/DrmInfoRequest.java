// SPDX-License-Identifier: GPL-3.0-only
package android.drm;

import java.util.HashMap;
import java.util.Iterator;

/** Request parameters used to acquire DRM registration or rights information. */
@Deprecated
public class DrmInfoRequest {
    public static final int TYPE_REGISTRATION_INFO = 1;
    public static final int TYPE_UNREGISTRATION_INFO = 2;
    public static final int TYPE_RIGHTS_ACQUISITION_INFO = 3;
    public static final int TYPE_RIGHTS_ACQUISITION_PROGRESS_INFO = 4;

    public static final String ACCOUNT_ID = "account_id";
    public static final String SUBSCRIPTION_ID = "subscription_id";

    private final int mInfoType;
    private final String mMimeType;
    private final HashMap<String, Object> mRequestInformation = new HashMap<String, Object>();

    public DrmInfoRequest(int infoType, String mimeType) {
        this.mInfoType = infoType;
        this.mMimeType = mimeType;
        if (!isValid()) {
            throw new IllegalArgumentException(
                    "infoType: " + infoType + ", mimeType: " + mimeType);
        }
    }

    public String getMimeType() {
        return mMimeType;
    }

    public int getInfoType() {
        return mInfoType;
    }

    public void put(String key, Object value) {
        mRequestInformation.put(key, value);
    }

    public Object get(String key) {
        return mRequestInformation.get(key);
    }

    public Iterator<String> keyIterator() {
        return mRequestInformation.keySet().iterator();
    }

    public Iterator<Object> iterator() {
        return mRequestInformation.values().iterator();
    }

    boolean isValid() {
        return mMimeType != null
                && !mMimeType.isEmpty()
                && mRequestInformation != null
                && isValidType(mInfoType);
    }

    static boolean isValidType(int infoType) {
        switch (infoType) {
            case TYPE_REGISTRATION_INFO:
            case TYPE_UNREGISTRATION_INFO:
            case TYPE_RIGHTS_ACQUISITION_INFO:
            case TYPE_RIGHTS_ACQUISITION_PROGRESS_INFO:
                return true;
            default:
                return false;
        }
    }
}
