// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Handler;
import android.os.Looper;
import android.telephony.emergency.EmergencyNumber;
import android.telephony.ims.ImsReasonInfo;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Legacy phone-state listener for hosts without a modem. */
@Deprecated
public class PhoneStateListener {
    @Deprecated
    public static final int LISTEN_NONE = 0;
    @Deprecated
    public static final int LISTEN_SERVICE_STATE = 0x00000001;
    @Deprecated
    public static final int LISTEN_SIGNAL_STRENGTH = 0x00000002;
    @Deprecated
    public static final int LISTEN_MESSAGE_WAITING_INDICATOR = 0x00000004;
    @Deprecated
    public static final int LISTEN_CALL_FORWARDING_INDICATOR = 0x00000008;
    @Deprecated
    public static final int LISTEN_CELL_LOCATION = 0x00000010;
    @Deprecated
    public static final int LISTEN_CALL_STATE = 0x00000020;
    @Deprecated
    public static final int LISTEN_DATA_CONNECTION_STATE = 0x00000040;
    @Deprecated
    public static final int LISTEN_DATA_ACTIVITY = 0x00000080;
    @Deprecated
    public static final int LISTEN_SIGNAL_STRENGTHS = 0x00000100;
    @Deprecated
    public static final int LISTEN_CELL_INFO = 0x00000400;
    @Deprecated
    public static final int LISTEN_PRECISE_DATA_CONNECTION_STATE = 0x00001000;
    @Deprecated
    public static final int LISTEN_USER_MOBILE_DATA_STATE = 0x00080000;
    @Deprecated
    public static final int LISTEN_DISPLAY_INFO_CHANGED = 0x00100000;
    @Deprecated
    public static final int LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE = 0x00400000;
    @Deprecated
    public static final int LISTEN_EMERGENCY_NUMBER_LIST = 0x01000000;
    @Deprecated
    public static final int LISTEN_CALL_DISCONNECT_CAUSES = 0x02000000;
    @Deprecated
    public static final int LISTEN_IMS_CALL_DISCONNECT_CAUSES = 0x08000000;
    @Deprecated
    public static final int LISTEN_REGISTRATION_FAILURE = 0x40000000;
    @Deprecated
    public static final int LISTEN_BARRING_INFO = 0x80000000;

    final Handler handler;
    final Executor executor;

    @Deprecated
    public PhoneStateListener() {
        this(Looper.myLooper());
    }

    public PhoneStateListener(Looper looper) {
        handler = new Handler(looper);
        executor = null;
    }

    @Deprecated
    public PhoneStateListener(Executor executor) {
        Objects.requireNonNull(executor, "PhoneStateListener Executor must be non-null");
        this.handler = null;
        this.executor = executor;
    }

    void postCallback(Runnable r) {
        if (executor != null) {
            executor.execute(r);
        } else {
            handler.post(r);
        }
    }

    @Deprecated
    public void onServiceStateChanged(ServiceState serviceState) {
    }

    @Deprecated
    public void onSignalStrengthChanged(int asu) {
    }

    @Deprecated
    public void onMessageWaitingIndicatorChanged(boolean mwi) {
    }

    @Deprecated
    public void onCallForwardingIndicatorChanged(boolean cfi) {
    }

    @Deprecated
    public void onCellLocationChanged(CellLocation location) {
    }

    @Deprecated
    public void onCallStateChanged(int state, String phoneNumber) {
    }

    @Deprecated
    public void onDataConnectionStateChanged(int state) {
    }

    @Deprecated
    public void onDataConnectionStateChanged(int state, int networkType) {
    }

    @Deprecated
    public void onDataActivity(int direction) {
    }

    @Deprecated
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
    }

    @Deprecated
    public void onCellInfoChanged(List<CellInfo> cellInfo) {
    }

    @Deprecated
    public void onCallDisconnectCauseChanged(int disconnectCause, int preciseDisconnectCause) {
    }

    @Deprecated
    public void onImsCallDisconnectCauseChanged(ImsReasonInfo imsReasonInfo) {
    }

    @Deprecated
    public void onPreciseDataConnectionStateChanged(
            PreciseDataConnectionState dataConnectionState) {
    }

    @Deprecated
    public void onUserMobileDataStateChanged(boolean enabled) {
    }

    @Deprecated
    public void onDisplayInfoChanged(TelephonyDisplayInfo telephonyDisplayInfo) {
    }

    @Deprecated
    public void onEmergencyNumberListChanged(
            Map<Integer, List<EmergencyNumber>> emergencyNumberList) {
    }

    @Deprecated
    public void onActiveDataSubscriptionIdChanged(int subId) {
    }

    @Deprecated
    public void onRegistrationFailed(CellIdentity cellIdentity, String chosenPlmn, int domain,
            int causeCode, int additionalCauseCode) {
    }

    @Deprecated
    public void onBarringInfoChanged(BarringInfo barringInfo) {
    }
}
