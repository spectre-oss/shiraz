// SPDX-License-Identifier: GPL-3.0-only
package android.drm;

import java.util.HashMap;

/** Base event class for asynchronous DRM notifications and errors. */
@Deprecated
public class DrmEvent {
    public static final int TYPE_ALL_RIGHTS_REMOVED = 1001;
    public static final int TYPE_DRM_INFO_PROCESSED = 1002;

    public static final String DRM_INFO_STATUS_OBJECT = "drm_info_status_object";
    public static final String DRM_INFO_OBJECT = "drm_info_object";

    private final int mUniqueId;
    private final int mType;
    private String mMessage = "";
    private HashMap<String, Object> mAttributes = new HashMap<String, Object>();

    protected DrmEvent(
            int uniqueId, int type, String message, HashMap<String, Object> attributes) {
        this.mUniqueId = uniqueId;
        this.mType = type;
        if (message != null) {
            this.mMessage = message;
        }
        if (attributes != null) {
            this.mAttributes = attributes;
        }
    }

    protected DrmEvent(int uniqueId, int type, String message) {
        this.mUniqueId = uniqueId;
        this.mType = type;
        if (message != null) {
            this.mMessage = message;
        }
    }

    public int getUniqueId() {
        return mUniqueId;
    }

    public int getType() {
        return mType;
    }

    public String getMessage() {
        return mMessage;
    }

    public Object getAttribute(String key) {
        return mAttributes.get(key);
    }
}
