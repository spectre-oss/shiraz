// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.mbms;

import java.util.List;

public class MbmsDownloadSessionCallback {
    public MbmsDownloadSessionCallback() {}

    public void onError(int errorCode, String message) {}

    public void onFileServicesUpdated(List<FileServiceInfo> services) {}

    public void onMiddlewareReady() {}
}
