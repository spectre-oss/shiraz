// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import android.os.Bundle;
import android.os.Handler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class WifiAwareManager {
    public static final String ACTION_WIFI_AWARE_STATE_CHANGED =
            "android.net.wifi.aware.action.WIFI_AWARE_STATE_CHANGED";
    public static final String ACTION_WIFI_AWARE_RESOURCE_CHANGED =
            "android.net.wifi.aware.action.WIFI_AWARE_RESOURCE_CHANGED";
    public static final String EXTRA_AWARE_RESOURCES =
            "android.net.wifi.aware.extra.AWARE_RESOURCES";

    public static final int WIFI_AWARE_DATA_PATH_ROLE_INITIATOR = 0;
    public static final int WIFI_AWARE_DATA_PATH_ROLE_RESPONDER = 1;

    public static final int WIFI_AWARE_DISCOVERY_LOST_REASON_UNKNOWN = 0;
    public static final int WIFI_AWARE_DISCOVERY_LOST_REASON_PEER_NOT_VISIBLE = 1;

    private volatile boolean mOpportunisticModeEnabled = false;
    private final Set<String> mPairedDevices = new LinkedHashSet<String>();

    WifiAwareManager() {}

    public boolean isAvailable() {
        return false;
    }

    public boolean isDeviceAttached() {
        return false;
    }

    public boolean isSetChannelOnDataPathSupported() {
        return false;
    }

    public boolean isInstantCommunicationModeEnabled() {
        return false;
    }

    public Characteristics getCharacteristics() {
        return new Characteristics(new Bundle());
    }

    public AwareResources getAvailableAwareResources() {
        return new AwareResources(0, 0, 0);
    }

    public void attach(AttachCallback attachCallback, Handler handler) {
        attach(attachCallback, null, handler);
    }

    public void attach(
            final AttachCallback attachCallback,
            IdentityChangedListener identityChangedListener,
            Handler handler) {
        Objects.requireNonNull(attachCallback, "attachCallback");
        if (handler != null) {
            handler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            attachCallback.onAttachFailed();
                        }
                    });
        } else {
            attachCallback.onAttachFailed();
        }
    }

    public void setOpportunisticModeEnabled(boolean enabled) {
        mOpportunisticModeEnabled = enabled;
    }

    public void isOpportunisticModeEnabled(
            Executor executor, final Consumer<Boolean> resultsCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(resultsCallback, "resultsCallback");
        final boolean enabled = mOpportunisticModeEnabled;
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        resultsCallback.accept(Boolean.valueOf(enabled));
                    }
                });
    }

    public void resetPairedDevices() {
        synchronized (mPairedDevices) {
            mPairedDevices.clear();
        }
    }

    public void removePairedDevice(String alias) {
        Objects.requireNonNull(alias, "alias");
        synchronized (mPairedDevices) {
            mPairedDevices.remove(alias);
        }
    }

    public void getPairedDevices(Executor executor, final Consumer<List<String>> resultsCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(resultsCallback, "resultsCallback");
        final List<String> snapshot;
        synchronized (mPairedDevices) {
            snapshot = Collections.unmodifiableList(new ArrayList<String>(mPairedDevices));
        }
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        resultsCallback.accept(snapshot);
                    }
                });
    }
}
