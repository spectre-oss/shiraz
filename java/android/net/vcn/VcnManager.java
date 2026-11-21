// SPDX-License-Identifier: GPL-3.0-only
package android.net.vcn;

import android.os.ParcelUuid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class VcnManager {
    public static final int VCN_ERROR_CODE_INTERNAL_ERROR = 0;
    public static final int VCN_ERROR_CODE_CONFIG_ERROR = 1;
    public static final int VCN_ERROR_CODE_NETWORK_ERROR = 2;

    public static final int VCN_STATUS_CODE_NOT_CONFIGURED = 0;
    public static final int VCN_STATUS_CODE_INACTIVE = 1;
    public static final int VCN_STATUS_CODE_ACTIVE = 2;
    public static final int VCN_STATUS_CODE_SAFE_MODE = 3;

    private final Map<ParcelUuid, VcnConfig> mConfigs =
            Collections.synchronizedMap(new LinkedHashMap<ParcelUuid, VcnConfig>());
    private final Map<VcnStatusCallback, CallbackEntry> mCallbacks =
            new ConcurrentHashMap<VcnStatusCallback, CallbackEntry>();

    VcnManager() {}

    public void setVcnConfig(ParcelUuid subscriptionGroup, VcnConfig config) throws IOException {
        Objects.requireNonNull(subscriptionGroup, "subscriptionGroup was null");
        Objects.requireNonNull(config, "config was null");
        mConfigs.put(subscriptionGroup, config);
        notifyStatus(subscriptionGroup, VCN_STATUS_CODE_ACTIVE);
    }

    public void clearVcnConfig(ParcelUuid subscriptionGroup) throws IOException {
        Objects.requireNonNull(subscriptionGroup, "subscriptionGroup was null");
        mConfigs.remove(subscriptionGroup);
        notifyStatus(subscriptionGroup, VCN_STATUS_CODE_NOT_CONFIGURED);
    }

    public List<ParcelUuid> getConfiguredSubscriptionGroups() {
        synchronized (mConfigs) {
            return Collections.unmodifiableList(new ArrayList<ParcelUuid>(mConfigs.keySet()));
        }
    }

    public void registerVcnStatusCallback(
            ParcelUuid subscriptionGroup,
            Executor executor,
            final VcnStatusCallback callback) {
        Objects.requireNonNull(subscriptionGroup, "subscriptionGroup was null");
        Objects.requireNonNull(executor, "executor was null");
        Objects.requireNonNull(callback, "callback was null");
        if (mCallbacks.putIfAbsent(callback, new CallbackEntry(subscriptionGroup, executor))
                != null) {
            throw new IllegalArgumentException("callback is already registered");
        }
        final int currentStatus =
                mConfigs.containsKey(subscriptionGroup)
                        ? VCN_STATUS_CODE_ACTIVE
                        : VCN_STATUS_CODE_NOT_CONFIGURED;
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onStatusChanged(currentStatus);
                    }
                });
    }

    public void unregisterVcnStatusCallback(VcnStatusCallback callback) {
        Objects.requireNonNull(callback, "callback was null");
        mCallbacks.remove(callback);
    }

    private void notifyStatus(ParcelUuid group, final int status) {
        for (Map.Entry<VcnStatusCallback, CallbackEntry> e : mCallbacks.entrySet()) {
            if (group.equals(e.getValue().group)) {
                final VcnStatusCallback cb = e.getKey();
                e.getValue().executor.execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                cb.onStatusChanged(status);
                            }
                        });
            }
        }
    }

    private static final class CallbackEntry {
        final ParcelUuid group;
        final Executor executor;

        CallbackEntry(ParcelUuid group, Executor executor) {
            this.group = group;
            this.executor = executor;
        }
    }

    public abstract static class VcnStatusCallback {
        public VcnStatusCallback() {}

        public abstract void onStatusChanged(int statusCode);

        public abstract void onGatewayConnectionError(
                String gatewayConnectionName, int errorCode, Throwable detail);
    }
}
