// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

public class NetworkScan {
    public static final int SUCCESS = 0;
    public static final int ERROR_MODEM_ERROR = 1;
    public static final int ERROR_INVALID_SCAN = 2;
    public static final int ERROR_MODEM_UNAVAILABLE = 3;
    public static final int ERROR_UNSUPPORTED = 4;
    public static final int ERROR_RADIO_INTERFACE_ERROR = 10000;
    public static final int ERROR_INVALID_SCANID = 10001;
    public static final int ERROR_INTERRUPTED = 10002;

    private final int mScanId;
    private final int mSubId;
    private volatile boolean mStopped;

    NetworkScan(int scanId, int subId) {
        mScanId = scanId;
        mSubId = subId;
    }

    public void stopScan() {
        mStopped = true;
    }

    int getScanId() {
        return mScanId;
    }

    int getSubId() {
        return mSubId;
    }

    boolean isStopped() {
        return mStopped;
    }
}
