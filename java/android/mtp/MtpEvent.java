// SPDX-License-Identifier: GPL-3.0-only
package android.mtp;

public class MtpEvent {
    public static final int EVENT_UNDEFINED = 16384;
    public static final int EVENT_CANCEL_TRANSACTION = 16385;
    public static final int EVENT_OBJECT_ADDED = 16386;
    public static final int EVENT_OBJECT_REMOVED = 16387;
    public static final int EVENT_STORE_ADDED = 16388;
    public static final int EVENT_STORE_REMOVED = 16389;
    public static final int EVENT_DEVICE_PROP_CHANGED = 16390;
    public static final int EVENT_OBJECT_INFO_CHANGED = 16391;
    public static final int EVENT_DEVICE_INFO_CHANGED = 16392;
    public static final int EVENT_REQUEST_OBJECT_TRANSFER = 16393;
    public static final int EVENT_STORE_FULL = 16394;
    public static final int EVENT_DEVICE_RESET = 16395;
    public static final int EVENT_STORAGE_INFO_CHANGED = 16396;
    public static final int EVENT_CAPTURE_COMPLETE = 16397;
    public static final int EVENT_UNREPORTED_STATUS = 16398;
    public static final int EVENT_OBJECT_PROP_CHANGED = 51201;
    public static final int EVENT_OBJECT_PROP_DESC_CHANGED = 51202;
    public static final int EVENT_OBJECT_REFERENCES_CHANGED = 51203;

    private int mEventCode = EVENT_UNDEFINED;
    private int mParameter1;
    private int mParameter2;
    private int mParameter3;

    private MtpEvent() {
    }

    /** @hide */
    public MtpEvent(int eventCode, int parameter1, int parameter2, int parameter3) {
        mEventCode = eventCode;
        mParameter1 = parameter1;
        mParameter2 = parameter2;
        mParameter3 = parameter3;
    }

    public int getEventCode() {
        return mEventCode;
    }

    public int getParameter1() {
        return mParameter1;
    }

    public int getParameter2() {
        return mParameter2;
    }

    public int getParameter3() {
        return mParameter3;
    }

    public int getObjectHandle() {
        switch (mEventCode) {
            case EVENT_OBJECT_ADDED:
            case EVENT_OBJECT_REMOVED:
            case EVENT_OBJECT_INFO_CHANGED:
            case EVENT_REQUEST_OBJECT_TRANSFER:
            case EVENT_OBJECT_PROP_CHANGED:
            case EVENT_OBJECT_REFERENCES_CHANGED:
                return mParameter1;
            default:
                throw new UnsupportedOperationException(
                        "Cannot obtain objectHandle for event: " + mEventCode);
        }
    }

    public int getStorageId() {
        switch (mEventCode) {
            case EVENT_STORE_ADDED:
            case EVENT_STORE_REMOVED:
            case EVENT_STORE_FULL:
            case EVENT_STORAGE_INFO_CHANGED:
                return mParameter1;
            default:
                throw new UnsupportedOperationException(
                        "Cannot obtain storageId for event: " + mEventCode);
        }
    }

    public int getDevicePropCode() {
        if (mEventCode == EVENT_DEVICE_PROP_CHANGED) {
            return mParameter1;
        }
        throw new UnsupportedOperationException(
                "Cannot obtain devicePropCode for event: " + mEventCode);
    }

    public int getTransactionId() {
        if (mEventCode == EVENT_CAPTURE_COMPLETE) {
            return mParameter1;
        }
        throw new UnsupportedOperationException(
                "Cannot obtain transactionId for event: " + mEventCode);
    }

    public int getObjectPropCode() {
        switch (mEventCode) {
            case EVENT_OBJECT_PROP_CHANGED:
                return mParameter2;
            case EVENT_OBJECT_PROP_DESC_CHANGED:
                return mParameter1;
            default:
                throw new UnsupportedOperationException(
                        "Cannot obtain objectPropCode for event: " + mEventCode);
        }
    }

    public int getObjectFormatCode() {
        if (mEventCode == EVENT_OBJECT_PROP_DESC_CHANGED) {
            return mParameter2;
        }
        throw new UnsupportedOperationException(
                "Cannot obtain objectFormatCode for event: " + mEventCode);
    }
}
