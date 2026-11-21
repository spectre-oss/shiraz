// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.mbms;

import java.util.List;

public class MbmsStreamingSessionCallback {
    public MbmsStreamingSessionCallback() {}

    public void onError(int errorCode, String message) {}

    public void onStreamingServicesUpdated(List<StreamingServiceInfo> services) {}

    public void onMiddlewareReady() {}
}
