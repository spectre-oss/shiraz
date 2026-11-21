// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.ims;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

public class ProvisioningManager {
    public abstract static class FeatureProvisioningCallback {
        public FeatureProvisioningCallback() {}

        public abstract void onFeatureProvisioningChanged(
                int capability, int tech, boolean isProvisioned);

        public abstract void onRcsFeatureProvisioningChanged(
                int capability, int tech, boolean isProvisioned);
    }

    private final int mSubId;
    private final Map<Long, Boolean> mMmTelStatus = new HashMap<Long, Boolean>();
    private final Map<Long, Boolean> mRcsStatus = new HashMap<Long, Boolean>();

    ProvisioningManager(int subId) {
        mSubId = subId;
    }

    private static long key(int capability, int tech) {
        return (((long) capability) << 32) | (tech & 0xffffffffL);
    }

    public void registerFeatureProvisioningChangedCallback(
            Executor executor, FeatureProvisioningCallback callback) throws ImsException {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        throw new ImsException("IMS provisioning service unavailable on host", ImsException.CODE_ERROR_SERVICE_UNAVAILABLE);
    }

    public void unregisterFeatureProvisioningChangedCallback(FeatureProvisioningCallback callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
    }

    public void setProvisioningStatusForCapability(
            int capability, int tech, boolean isProvisioned) {
        synchronized (mMmTelStatus) {
            mMmTelStatus.put(Long.valueOf(key(capability, tech)), Boolean.valueOf(isProvisioned));
        }
    }

    public boolean getProvisioningStatusForCapability(int capability, int tech) {
        synchronized (mMmTelStatus) {
            Boolean v = mMmTelStatus.get(Long.valueOf(key(capability, tech)));
            return v != null && v.booleanValue();
        }
    }

    public boolean getRcsProvisioningStatusForCapability(int capability, int tech) {
        synchronized (mRcsStatus) {
            Boolean v = mRcsStatus.get(Long.valueOf(key(capability, tech)));
            return v != null && v.booleanValue();
        }
    }

    public void setRcsProvisioningStatusForCapability(
            int capability, int tech, boolean isProvisioned) {
        synchronized (mRcsStatus) {
            mRcsStatus.put(Long.valueOf(key(capability, tech)), Boolean.valueOf(isProvisioned));
        }
    }

    public boolean isProvisioningRequiredForCapability(int capability, int tech) {
        return false;
    }

    public boolean isRcsProvisioningRequiredForCapability(int capability, int tech) {
        return false;
    }
}
