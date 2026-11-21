// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.hotspot2;

import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class PasspointConfiguration implements Parcelable {
    public static final Creator<PasspointConfiguration> CREATOR =
            new Creator<PasspointConfiguration>() {
                @Override
                public PasspointConfiguration createFromParcel(Parcel in) {
                    PasspointConfiguration pc = new PasspointConfiguration();
                    pc.mHomeSp = in.readTypedObject(HomeSp.CREATOR);
                    pc.mCredential = in.readTypedObject(Credential.CREATOR);
                    pc.mSubscriptionExpirationTimeInMillis = in.readLong();
                    pc.mDecoratedIdentityPrefix = in.readString();
                    return pc;
                }

                @Override
                public PasspointConfiguration[] newArray(int size) {
                    return new PasspointConfiguration[size];
                }
            };

    private HomeSp mHomeSp;
    private Credential mCredential;
    private long mSubscriptionExpirationTimeInMillis = Long.MIN_VALUE;
    private String mDecoratedIdentityPrefix;

    public PasspointConfiguration() {}

    public PasspointConfiguration(PasspointConfiguration source) {
        if (source != null) {
            this.mHomeSp = source.mHomeSp == null ? null : new HomeSp(source.mHomeSp);
            this.mCredential =
                    source.mCredential == null ? null : new Credential(source.mCredential);
            this.mSubscriptionExpirationTimeInMillis =
                    source.mSubscriptionExpirationTimeInMillis;
            this.mDecoratedIdentityPrefix = source.mDecoratedIdentityPrefix;
        }
    }

    public void setHomeSp(HomeSp homeSp) {
        this.mHomeSp = homeSp;
    }

    public HomeSp getHomeSp() {
        return mHomeSp;
    }

    public void setCredential(Credential credential) {
        this.mCredential = credential;
    }

    public Credential getCredential() {
        return mCredential;
    }

    @Deprecated
    public void setSubscriptionExpirationTimeInMillis(long subscriptionExpirationTimeInMillis) {
        this.mSubscriptionExpirationTimeInMillis = subscriptionExpirationTimeInMillis;
    }

    public long getSubscriptionExpirationTimeMillis() {
        return mSubscriptionExpirationTimeInMillis;
    }

    public boolean isOsuProvisioned() {
        return false;
    }

    public String getUniqueId() {
        if (mHomeSp == null || mHomeSp.getFqdn() == null) {
            throw new IllegalStateException("HomeSp or FQDN is not initialized");
        }
        return mHomeSp.getFqdn() + "_" + mHomeSp.getFqdn().hashCode();
    }

    public void setDecoratedIdentityPrefix(String decoratedIdentityPrefix) {
        this.mDecoratedIdentityPrefix = decoratedIdentityPrefix;
    }

    public String getDecoratedIdentityPrefix() {
        return mDecoratedIdentityPrefix;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mHomeSp, flags);
        dest.writeTypedObject(mCredential, flags);
        dest.writeLong(mSubscriptionExpirationTimeInMillis);
        dest.writeString(mDecoratedIdentityPrefix);
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject) {
            return true;
        }
        if (!(thatObject instanceof PasspointConfiguration)) {
            return false;
        }
        PasspointConfiguration that = (PasspointConfiguration) thatObject;
        return mSubscriptionExpirationTimeInMillis == that.mSubscriptionExpirationTimeInMillis
                && Objects.equals(mHomeSp, that.mHomeSp)
                && Objects.equals(mCredential, that.mCredential)
                && Objects.equals(mDecoratedIdentityPrefix, that.mDecoratedIdentityPrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mHomeSp,
                mCredential,
                Long.valueOf(mSubscriptionExpirationTimeInMillis),
                mDecoratedIdentityPrefix);
    }

    @Override
    public String toString() {
        return "PasspointConfiguration{homeSp=" + mHomeSp + ", credential=" + mCredential + "}";
    }
}
