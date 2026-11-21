// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.mbms;

public interface GroupCallCallback {
    int SIGNAL_STRENGTH_UNAVAILABLE = -1;

    default void onError(int errorCode, String message) {}

    default void onGroupCallStateChanged(int state, int reason) {}

    default void onBroadcastSignalStrengthUpdated(int signalStrength) {}
}
