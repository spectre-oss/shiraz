// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Network IP assignment and HTTP proxy configuration. */
public final class IpConfiguration implements Parcelable {
    private final StaticIpConfiguration mStaticIpConfiguration;
    private final ProxyInfo mHttpProxy;

    IpConfiguration(StaticIpConfiguration staticIpConfiguration, ProxyInfo httpProxy) {
        this.mStaticIpConfiguration = staticIpConfiguration;
        this.mHttpProxy = httpProxy;
    }

    public StaticIpConfiguration getStaticIpConfiguration() {
        return mStaticIpConfiguration;
    }

    public ProxyInfo getHttpProxy() {
        return mHttpProxy;
    }

    @Override
    public String toString() {
        return "IpConfiguration{staticIp=" + mStaticIpConfiguration + ", proxy=" + mHttpProxy + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IpConfiguration)) {
            return false;
        }
        IpConfiguration other = (IpConfiguration) o;
        return Objects.equals(mStaticIpConfiguration, other.mStaticIpConfiguration)
                && Objects.equals(mHttpProxy, other.mHttpProxy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStaticIpConfiguration, mHttpProxy);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mStaticIpConfiguration, flags);
        dest.writeTypedObject(mHttpProxy, flags);
    }

    public static final class Builder {
        private StaticIpConfiguration mStaticIpConfiguration;
        private ProxyInfo mHttpProxy;

        public Builder() {
        }

        public Builder setStaticIpConfiguration(StaticIpConfiguration config) {
            this.mStaticIpConfiguration = config;
            return this;
        }

        public Builder setHttpProxy(ProxyInfo proxyInfo) {
            this.mHttpProxy = proxyInfo;
            return this;
        }

        public IpConfiguration build() {
            return new IpConfiguration(mStaticIpConfiguration, mHttpProxy);
        }
    }

    public static final Parcelable.Creator<IpConfiguration> CREATOR =
            new Parcelable.Creator<IpConfiguration>() {
                @Override
                public IpConfiguration createFromParcel(Parcel in) {
                    return new IpConfiguration(
                            in.readTypedObject(StaticIpConfiguration.CREATOR),
                            in.readTypedObject(ProxyInfo.CREATOR));
                }

                @Override
                public IpConfiguration[] newArray(int size) {
                    return new IpConfiguration[size];
                }
            };
}
