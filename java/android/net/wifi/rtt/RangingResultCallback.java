// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.rtt;

import java.util.List;

public abstract class RangingResultCallback {
    public static final int STATUS_CODE_FAIL = 1;
    public static final int STATUS_CODE_FAIL_RTT_NOT_AVAILABLE = 2;

    public RangingResultCallback() {}

    public abstract void onRangingFailure(int code);

    public abstract void onRangingResults(List<RangingResult> results);
}
