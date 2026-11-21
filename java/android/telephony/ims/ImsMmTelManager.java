// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.ims;

import android.telephony.AccessNetworkConstants;
import android.telephony.SubscriptionManager;
import android.telephony.ims.feature.MmTelFeature;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class ImsMmTelManager implements RegistrationManager {
    public static final int WIFI_MODE_WIFI_ONLY = 0;
    public static final int WIFI_MODE_CELLULAR_PREFERRED = 1;
    public static final int WIFI_MODE_WIFI_PREFERRED = 2;

    public static class CapabilityCallback {
        public CapabilityCallback() {}

        public void onCapabilitiesStatusChanged(MmTelFeature.MmTelCapabilities capabilities) {}
    }

    private final int mSubId;

    ImsMmTelManager(int subId) {
        mSubId = subId;
    }

    public void registerImsRegistrationCallback(Executor executor, RegistrationCallback c)
            throws ImsException {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(c, "c cannot be null");
        if (!SubscriptionManager.isValidSubscriptionId(mSubId)) {
            throw new ImsException("Invalid subscription ID", ImsException.CODE_ERROR_INVALID_SUBSCRIPTION);
        }
        throw new ImsException("IMS service is unavailable on host", ImsException.CODE_ERROR_SERVICE_UNAVAILABLE);
    }

    public void unregisterImsRegistrationCallback(RegistrationCallback c) {
        Objects.requireNonNull(c, "c cannot be null");
    }

    public void getRegistrationState(Executor executor, final Consumer<Integer> stateCallback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(stateCallback, "stateCallback cannot be null");
        executor.execute(new Runnable() {
            public void run() {
                stateCallback.accept(Integer.valueOf(REGISTRATION_STATE_NOT_REGISTERED));
            }
        });
    }

    public void getRegistrationTransportType(
            Executor executor, final Consumer<Integer> transportTypeCallback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(transportTypeCallback, "transportTypeCallback cannot be null");
        executor.execute(new Runnable() {
            public void run() {
                transportTypeCallback.accept(Integer.valueOf(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
            }
        });
    }

    public void registerMmTelCapabilityCallback(Executor executor, CapabilityCallback c)
            throws ImsException {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(c, "c cannot be null");
        throw new ImsException("IMS service is unavailable on host", ImsException.CODE_ERROR_SERVICE_UNAVAILABLE);
    }

    public void unregisterMmTelCapabilityCallback(CapabilityCallback c) {
        Objects.requireNonNull(c, "c cannot be null");
    }

    public boolean isAdvancedCallingSettingEnabled() {
        return false;
    }

    public boolean isVtSettingEnabled() {
        return false;
    }

    public boolean isVoWiFiSettingEnabled() {
        return false;
    }

    public boolean isCrossSimCallingEnabled() throws ImsException {
        throw new ImsException("IMS service is unavailable on host", ImsException.CODE_ERROR_SERVICE_UNAVAILABLE);
    }

    public boolean isVoWiFiRoamingSettingEnabled() {
        return false;
    }

    public int getVoWiFiModeSetting() {
        return WIFI_MODE_CELLULAR_PREFERRED;
    }

    public boolean isTtyOverVolteEnabled() {
        return false;
    }

    public void registerImsStateCallback(Executor executor, final ImsStateCallback callback)
            throws ImsException {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        executor.execute(new Runnable() {
            public void run() {
                callback.onUnavailable(ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED);
            }
        });
    }

    public void unregisterImsStateCallback(ImsStateCallback callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
    }
}
