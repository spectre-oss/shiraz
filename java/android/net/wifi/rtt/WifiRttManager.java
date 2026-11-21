// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.rtt;

import android.os.Bundle;
import java.util.Objects;
import java.util.concurrent.Executor;

public class WifiRttManager {
    public static final String ACTION_WIFI_RTT_STATE_CHANGED =
            "android.net.wifi.rtt.action.WIFI_RTT_STATE_CHANGED";

    public static final String CHARACTERISTICS_KEY_BOOLEAN_ONE_SIDED_RTT =
            "key_one_sided_rtt";
    public static final String CHARACTERISTICS_KEY_BOOLEAN_LCI = "key_lci";
    public static final String CHARACTERISTICS_KEY_BOOLEAN_LCR = "key_lcr";
    public static final String CHARACTERISTICS_KEY_BOOLEAN_STA_RESPONDER =
            "key_sta_responder";
    public static final String CHARACTERISTICS_KEY_BOOLEAN_NTB_INITIATOR =
            "key_ntb_initiator";

    WifiRttManager() {}

    public boolean isAvailable() {
        return false;
    }

    public void startRanging(
            RangingRequest request,
            Executor executor,
            final RangingResultCallback callback) {
        Objects.requireNonNull(request, "Request cannot be null");
        Objects.requireNonNull(executor, "Executor cannot be null");
        Objects.requireNonNull(callback, "Callback cannot be null");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onRangingFailure(
                                RangingResultCallback.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);
                    }
                });
    }

    public Bundle getRttCharacteristics() {
        Bundle b = new Bundle();
        b.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_ONE_SIDED_RTT, false);
        b.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_LCI, false);
        b.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_LCR, false);
        b.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_STA_RESPONDER, false);
        b.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_NTB_INITIATOR, false);
        return b;
    }
}
