// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.telephony.emergency.EmergencyNumber;
import android.telephony.ims.ImsReasonInfo;
import java.util.List;
import java.util.Map;

public class TelephonyCallback {
    public TelephonyCallback() {
    }

    public interface ActiveDataSubscriptionIdListener {
        void onActiveDataSubscriptionIdChanged(int subId);
    }

    public interface BarringInfoListener {
        void onBarringInfoChanged(BarringInfo barringInfo);
    }

    public interface CallDisconnectCauseListener {
        void onCallDisconnectCauseChanged(int disconnectCause, int preciseDisconnectCause);
    }

    public interface CallForwardingIndicatorListener {
        void onCallForwardingIndicatorChanged(boolean cfi);
    }

    public interface CallStateListener {
        void onCallStateChanged(int state);
    }

    public interface CarrierNetworkListener {
        void onCarrierNetworkChange(boolean active);
    }

    public interface CellInfoListener {
        void onCellInfoChanged(List<CellInfo> cellInfo);
    }

    public interface CellLocationListener {
        void onCellLocationChanged(CellLocation location);
    }

    public interface DataActivationStateListener {
        void onDataActivationStateChanged(int state);
    }

    public interface DataActivityListener {
        void onDataActivity(int direction);
    }

    public interface DataConnectionStateListener {
        void onDataConnectionStateChanged(int state, int networkType);
    }

    public interface DisplayInfoListener {
        void onDisplayInfoChanged(TelephonyDisplayInfo telephonyDisplayInfo);
    }

    public interface EmergencyNumberListListener {
        void onEmergencyNumberListChanged(
                Map<Integer, List<EmergencyNumber>> emergencyNumberList);
    }

    public interface ImsCallDisconnectCauseListener {
        void onImsCallDisconnectCauseChanged(ImsReasonInfo imsReasonInfo);
    }

    public interface MessageWaitingIndicatorListener {
        void onMessageWaitingIndicatorChanged(boolean mwi);
    }

    public interface PhysicalChannelConfigListener {
        void onPhysicalChannelConfigChanged(List<PhysicalChannelConfig> configs);
    }

    public interface PreciseDataConnectionStateListener {
        void onPreciseDataConnectionStateChanged(PreciseDataConnectionState dataConnectionState);
    }

    public interface RegistrationFailedListener {
        void onRegistrationFailed(CellIdentity cellIdentity, String chosenPlmn, int domain,
                int causeCode, int additionalCauseCode);
    }

    public interface ServiceStateListener {
        void onServiceStateChanged(ServiceState serviceState);
    }

    public interface SignalStrengthsListener {
        void onSignalStrengthsChanged(SignalStrength signalStrength);
    }

    public interface UserMobileDataStateListener {
        void onUserMobileDataStateChanged(boolean enabled);
    }
}
