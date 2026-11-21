// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import java.util.List;

public final class TelephonyScanManager {
    public TelephonyScanManager() {
    }

    public abstract static class NetworkScanCallback {
        public NetworkScanCallback() {
        }

        public void onResults(List<CellInfo> results) {
        }

        public void onComplete() {
        }

        public void onError(int error) {
        }
    }
}
