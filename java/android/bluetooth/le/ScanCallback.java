// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth.le;
/** Application override hooks; the base callback intentionally consumes no events. */
public abstract class ScanCallback {
    public static final int SCAN_FAILED_ALREADY_STARTED=1,SCAN_FAILED_APPLICATION_REGISTRATION_FAILED=2,SCAN_FAILED_INTERNAL_ERROR=3,SCAN_FAILED_FEATURE_UNSUPPORTED=4,SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES=5,SCAN_FAILED_SCANNING_TOO_FREQUENTLY=6;
    public void onScanResult(int callbackType,ScanResult result){}
    public void onBatchScanResults(java.util.List<ScanResult> results){}
    public void onScanFailed(int errorCode){}
}
