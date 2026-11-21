// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth.le;

import android.app.PendingIntent;
import java.util.List;

public final class BluetoothLeScanner {
    public static final String EXTRA_LIST_SCAN_RESULT = "android.bluetooth.le.extra.LIST_SCAN_RESULT";
    public static final String EXTRA_ERROR_CODE = "android.bluetooth.le.extra.ERROR_CODE";
    public static final String EXTRA_CALLBACK_TYPE = "android.bluetooth.le.extra.CALLBACK_TYPE";

    BluetoothLeScanner() {}

    public void startScan(ScanCallback callback) {
        startScan(null, new ScanSettings.Builder().build(), callback);
    }

    public void startScan(List<ScanFilter> filters, ScanSettings settings, ScanCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback is null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings is null");
        }
        callback.onScanFailed(ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED);
    }

    public int startScan(List<ScanFilter> filters, ScanSettings settings, PendingIntent callbackIntent) {
        if (callbackIntent == null) {
            throw new IllegalArgumentException("callbackIntent is null");
        }
        return ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED;
    }

    public void stopScan(ScanCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback is null");
        }
    }

    public void stopScan(PendingIntent callbackIntent) {
        if (callbackIntent == null) {
            throw new IllegalArgumentException("callbackIntent is null");
        }
    }

    public void flushPendingScanResults(ScanCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null!");
        }
    }
}
