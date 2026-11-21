// SPDX-License-Identifier: GPL-3.0-only
package android.devicelock;

public final class DeviceId {
    public static final int DEVICE_ID_TYPE_IMEI = 0;
    public static final int DEVICE_ID_TYPE_MEID = 1;

    private final int mType;
    private final String mId;

    /** @hide */
    public DeviceId(int type, String id) {
        if (type != DEVICE_ID_TYPE_IMEI && type != DEVICE_ID_TYPE_MEID) {
            throw new IllegalArgumentException("Invalid device ID type: " + type);
        }
        if (id == null) {
            throw new NullPointerException("id");
        }
        mType = type;
        mId = id;
    }

    public int getType() {
        return mType;
    }

    public String getId() {
        return mId;
    }
}
