// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.mbms;

import java.util.List;

public class GroupCall implements AutoCloseable {
    public static final int REASON_NONE = 0;
    public static final int REASON_BY_USER_REQUEST = 1;
    public static final int REASON_FREQUENCY_CONFLICT = 3;
    public static final int REASON_OUT_OF_MEMORY = 4;
    public static final int REASON_NOT_CONNECTED_TO_HOMECARRIER_LTE = 5;
    public static final int REASON_LEFT_MBMS_BROADCAST_AREA = 6;

    public static final int STATE_STOPPED = 1;
    public static final int STATE_STARTED = 2;
    public static final int STATE_STALLED = 3;

    private final long mTmgi;

    GroupCall(long tmgi) {
        mTmgi = tmgi;
    }

    public long getTmgi() {
        return mTmgi;
    }

    public void updateGroupCall(List<Integer> saiList, List<Integer> frequencyList) {}

    public void close() {}
}
