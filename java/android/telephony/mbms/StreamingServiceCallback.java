// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.mbms;

public class StreamingServiceCallback {
    public static final int SIGNAL_STRENGTH_UNAVAILABLE = -1;

    public StreamingServiceCallback() {}

    public void onError(int errorCode, String message) {}

    public void onStreamStateUpdated(int state, int reason) {}

    public void onMediaDescriptionUpdated() {}

    public void onBroadcastSignalStrengthUpdated(int signalStrength) {}

    public void onStreamMethodUpdated(int methodType) {}
}
