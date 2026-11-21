// SPDX-License-Identifier: GPL-3.0-only
package android.net.vcn;

import android.net.ipsec.ike.IkeTunnelConnectionParams;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class VcnGatewayConnectionConfig {
    public static final int VCN_GATEWAY_OPTION_ENABLE_DATA_STALL_RECOVERY_WITH_MOBILITY = 0;

    private static final int DEFAULT_MAX_MTU = 1500;
    private static final int MIN_MTU_V6 = 1280;
    private static final int MIN_UDP_PORT_4500_NAT_TIMEOUT_UNSET = -1;
    private static final long[] DEFAULT_RETRY_INTERVALS_MS = {
        1000L, 2000L, 5000L, 30000L, 60000L, 300000L, 900000L
    };

    private final String mGatewayConnectionName;
    private final IkeTunnelConnectionParams mTunnelConnectionParams;
    private final TreeSet<Integer> mExposedCapabilities;
    private final List<VcnUnderlyingNetworkTemplate> mUnderlyingNetworkTemplates;
    private final long[] mRetryIntervalsMs;
    private final int mMaxMtu;
    private final int mMinUdpPort4500NatTimeoutSeconds;
    private final boolean mIsSafeModeEnabled;
    private final Set<Integer> mGatewayOptions;

    VcnGatewayConnectionConfig(Builder b) {
        this.mGatewayConnectionName = b.mGatewayConnectionName;
        this.mTunnelConnectionParams = b.mTunnelConnectionParams;
        this.mExposedCapabilities = new TreeSet<Integer>(b.mExposedCapabilities);
        this.mUnderlyingNetworkTemplates =
                Collections.unmodifiableList(
                        new ArrayList<VcnUnderlyingNetworkTemplate>(
                                b.mUnderlyingNetworkTemplates));
        this.mRetryIntervalsMs = b.mRetryIntervalsMs.clone();
        this.mMaxMtu = b.mMaxMtu;
        this.mMinUdpPort4500NatTimeoutSeconds = b.mMinUdpPort4500NatTimeoutSeconds;
        this.mIsSafeModeEnabled = b.mIsSafeModeEnabled;
        this.mGatewayOptions =
                Collections.unmodifiableSet(new LinkedHashSet<Integer>(b.mGatewayOptions));
    }

    public String getGatewayConnectionName() {
        return mGatewayConnectionName;
    }

    public int[] getExposedCapabilities() {
        int[] arr = new int[mExposedCapabilities.size()];
        int i = 0;
        for (Integer cap : mExposedCapabilities) {
            arr[i++] = cap.intValue();
        }
        return arr;
    }

    public List<VcnUnderlyingNetworkTemplate> getVcnUnderlyingNetworkPriorities() {
        return mUnderlyingNetworkTemplates;
    }

    public long[] getRetryIntervalsMillis() {
        return mRetryIntervalsMs.clone();
    }

    public int getMaxMtu() {
        return mMaxMtu;
    }

    public int getMinUdpPort4500NatTimeoutSeconds() {
        return mMinUdpPort4500NatTimeoutSeconds;
    }

    public boolean isSafeModeEnabled() {
        return mIsSafeModeEnabled;
    }

    public boolean hasGatewayOption(int option) {
        return mGatewayOptions.contains(Integer.valueOf(option));
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mGatewayConnectionName,
                mTunnelConnectionParams,
                mExposedCapabilities,
                mUnderlyingNetworkTemplates,
                Integer.valueOf(Arrays.hashCode(mRetryIntervalsMs)),
                Integer.valueOf(mMaxMtu),
                Integer.valueOf(mMinUdpPort4500NatTimeoutSeconds),
                Boolean.valueOf(mIsSafeModeEnabled),
                mGatewayOptions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VcnGatewayConnectionConfig)) {
            return false;
        }
        VcnGatewayConnectionConfig other = (VcnGatewayConnectionConfig) o;
        return mMaxMtu == other.mMaxMtu
                && mMinUdpPort4500NatTimeoutSeconds == other.mMinUdpPort4500NatTimeoutSeconds
                && mIsSafeModeEnabled == other.mIsSafeModeEnabled
                && Objects.equals(mGatewayConnectionName, other.mGatewayConnectionName)
                && Objects.equals(mTunnelConnectionParams, other.mTunnelConnectionParams)
                && Objects.equals(mExposedCapabilities, other.mExposedCapabilities)
                && Objects.equals(mUnderlyingNetworkTemplates, other.mUnderlyingNetworkTemplates)
                && Arrays.equals(mRetryIntervalsMs, other.mRetryIntervalsMs)
                && Objects.equals(mGatewayOptions, other.mGatewayOptions);
    }

    public static final class Builder {
        final String mGatewayConnectionName;
        final IkeTunnelConnectionParams mTunnelConnectionParams;
        final Set<Integer> mExposedCapabilities = new TreeSet<Integer>();
        final List<VcnUnderlyingNetworkTemplate> mUnderlyingNetworkTemplates =
                new ArrayList<VcnUnderlyingNetworkTemplate>();
        long[] mRetryIntervalsMs = DEFAULT_RETRY_INTERVALS_MS.clone();
        int mMaxMtu = DEFAULT_MAX_MTU;
        int mMinUdpPort4500NatTimeoutSeconds = MIN_UDP_PORT_4500_NAT_TIMEOUT_UNSET;
        boolean mIsSafeModeEnabled = true;
        final Set<Integer> mGatewayOptions = new LinkedHashSet<Integer>();

        public Builder(
                String gatewayConnectionName, IkeTunnelConnectionParams tunnelConnectionParams) {
            Objects.requireNonNull(gatewayConnectionName, "gatewayConnectionName was null");
            if (gatewayConnectionName.isEmpty()) {
                throw new IllegalArgumentException("gatewayConnectionName cannot be empty");
            }
            this.mGatewayConnectionName = gatewayConnectionName;
            this.mTunnelConnectionParams =
                    Objects.requireNonNull(
                            tunnelConnectionParams, "tunnelConnectionParams was null");
        }

        public Builder addExposedCapability(int exposedCapability) {
            mExposedCapabilities.add(Integer.valueOf(exposedCapability));
            return this;
        }

        public Builder removeExposedCapability(int exposedCapability) {
            mExposedCapabilities.remove(Integer.valueOf(exposedCapability));
            return this;
        }

        public Builder setVcnUnderlyingNetworkPriorities(
                List<VcnUnderlyingNetworkTemplate> underlyingNetworkTemplates) {
            Objects.requireNonNull(
                    underlyingNetworkTemplates, "underlyingNetworkTemplates was null");
            mUnderlyingNetworkTemplates.clear();
            mUnderlyingNetworkTemplates.addAll(underlyingNetworkTemplates);
            return this;
        }

        public Builder setRetryIntervalsMillis(long[] retryIntervalsMs) {
            Objects.requireNonNull(retryIntervalsMs, "retryIntervalsMs was null");
            if (retryIntervalsMs.length == 0) {
                throw new IllegalArgumentException("retryIntervalsMs cannot be empty");
            }
            this.mRetryIntervalsMs = retryIntervalsMs.clone();
            return this;
        }

        public Builder setMaxMtu(int maxMtu) {
            if (maxMtu < MIN_MTU_V6) {
                throw new IllegalArgumentException("maxMtu must be at least " + MIN_MTU_V6);
            }
            this.mMaxMtu = maxMtu;
            return this;
        }

        public Builder setMinUdpPort4500NatTimeoutSeconds(int minUdpPort4500NatTimeoutSeconds) {
            if (minUdpPort4500NatTimeoutSeconds < 120) {
                throw new IllegalArgumentException("Timeout must be at least 120s");
            }
            this.mMinUdpPort4500NatTimeoutSeconds = minUdpPort4500NatTimeoutSeconds;
            return this;
        }

        public Builder addGatewayOption(int option) {
            validateGatewayOption(option);
            mGatewayOptions.add(Integer.valueOf(option));
            return this;
        }

        public Builder removeGatewayOption(int option) {
            validateGatewayOption(option);
            mGatewayOptions.remove(Integer.valueOf(option));
            return this;
        }

        public Builder setSafeModeEnabled(boolean isSafeModeEnabled) {
            this.mIsSafeModeEnabled = isSafeModeEnabled;
            return this;
        }

        private static void validateGatewayOption(int option) {
            if (option != VCN_GATEWAY_OPTION_ENABLE_DATA_STALL_RECOVERY_WITH_MOBILITY) {
                throw new IllegalArgumentException("Invalid vcn gateway option: " + option);
            }
        }

        public VcnGatewayConnectionConfig build() {
            if (mExposedCapabilities.isEmpty()) {
                throw new IllegalArgumentException("Exposed capabilities cannot be empty");
            }
            if (mUnderlyingNetworkTemplates.isEmpty()) {
                mUnderlyingNetworkTemplates.add(
                        new VcnWifiUnderlyingNetworkTemplate.Builder().build());
                mUnderlyingNetworkTemplates.add(
                        new VcnCellUnderlyingNetworkTemplate.Builder().build());
            }
            return new VcnGatewayConnectionConfig(this);
        }
    }
}
