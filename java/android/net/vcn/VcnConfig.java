// SPDX-License-Identifier: GPL-3.0-only
package android.net.vcn;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class VcnConfig implements Parcelable {
    public static final Creator<VcnConfig> CREATOR =
            new Creator<VcnConfig>() {
                @Override
                public VcnConfig createFromParcel(Parcel in) {
                    String pkg = in.readString();
                    int transportCount = in.readInt();
                    Set<Integer> transports = new LinkedHashSet<Integer>(transportCount);
                    for (int i = 0; i < transportCount; i++) {
                        transports.add(Integer.valueOf(in.readInt()));
                    }
                    return new VcnConfig(
                            pkg,
                            Collections.<VcnGatewayConnectionConfig>emptySet(),
                            transports);
                }

                @Override
                public VcnConfig[] newArray(int size) {
                    return new VcnConfig[size];
                }
            };

    private final String mPackageName;
    private final Set<VcnGatewayConnectionConfig> mGatewayConnectionConfigs;
    private final Set<Integer> mRestrictedTransports;

    VcnConfig(
            String packageName,
            Set<VcnGatewayConnectionConfig> gatewayConnectionConfigs,
            Set<Integer> restrictedTransports) {
        this.mPackageName = packageName;
        this.mGatewayConnectionConfigs =
                Collections.unmodifiableSet(
                        new LinkedHashSet<VcnGatewayConnectionConfig>(gatewayConnectionConfigs));
        this.mRestrictedTransports =
                Collections.unmodifiableSet(new LinkedHashSet<Integer>(restrictedTransports));
    }

    public Set<VcnGatewayConnectionConfig> getGatewayConnectionConfigs() {
        return mGatewayConnectionConfigs;
    }

    public Set<Integer> getRestrictedUnderlyingNetworkTransports() {
        return mRestrictedTransports;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPackageName, mGatewayConnectionConfigs, mRestrictedTransports);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VcnConfig)) {
            return false;
        }
        VcnConfig other = (VcnConfig) o;
        return Objects.equals(mPackageName, other.mPackageName)
                && Objects.equals(mGatewayConnectionConfigs, other.mGatewayConnectionConfigs)
                && Objects.equals(mRestrictedTransports, other.mRestrictedTransports);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mPackageName);
        out.writeInt(mRestrictedTransports.size());
        for (Integer t : mRestrictedTransports) {
            out.writeInt(t.intValue());
        }
    }

    public static final class Builder {
        private final String mPackageName;
        private final Set<VcnGatewayConnectionConfig> mGatewayConnectionConfigs =
                new LinkedHashSet<VcnGatewayConnectionConfig>();
        private final Set<Integer> mRestrictedTransports = new LinkedHashSet<Integer>();

        public Builder(Context context) {
            Objects.requireNonNull(context, "context was null");
            this.mPackageName = context.getOpPackageName();
        }

        public Builder addGatewayConnectionConfig(
                VcnGatewayConnectionConfig gatewayConnectionConfig) {
            Objects.requireNonNull(gatewayConnectionConfig, "gatewayConnectionConfig was null");
            for (VcnGatewayConnectionConfig existing : mGatewayConnectionConfigs) {
                if (existing.getGatewayConnectionName()
                        .equals(gatewayConnectionConfig.getGatewayConnectionName())) {
                    throw new IllegalArgumentException(
                            "Duplicate gateway connection name: "
                                    + gatewayConnectionConfig.getGatewayConnectionName());
                }
            }
            mGatewayConnectionConfigs.add(gatewayConnectionConfig);
            return this;
        }

        public Builder setRestrictedUnderlyingNetworkTransports(Set<Integer> transports) {
            Objects.requireNonNull(transports, "transports was null");
            mRestrictedTransports.clear();
            mRestrictedTransports.addAll(transports);
            return this;
        }

        public VcnConfig build() {
            if (mGatewayConnectionConfigs.isEmpty()) {
                throw new IllegalArgumentException(
                        "Must specify at least one VcnGatewayConnectionConfig");
            }
            return new VcnConfig(mPackageName, mGatewayConnectionConfigs, mRestrictedTransports);
        }
    }
}
