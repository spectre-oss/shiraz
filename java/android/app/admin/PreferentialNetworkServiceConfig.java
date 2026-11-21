// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class PreferentialNetworkServiceConfig implements Parcelable {
    public static final int PREFERENTIAL_NETWORK_ID_1 = 1;
    public static final int PREFERENTIAL_NETWORK_ID_2 = 2;
    public static final int PREFERENTIAL_NETWORK_ID_3 = 3;
    public static final int PREFERENTIAL_NETWORK_ID_4 = 4;
    public static final int PREFERENTIAL_NETWORK_ID_5 = 5;

    private final boolean mIsEnabled;
    private final boolean mAllowFallbackToDefaultConnection;
    private final boolean mShouldBlockNonMatchingNetworks;
    private final int[] mIncludedUids;
    private final int[] mExcludedUids;
    private final int mNetworkId;

    private PreferentialNetworkServiceConfig(
            boolean isEnabled,
            boolean allowFallbackToDefaultConnection,
            boolean shouldBlockNonMatchingNetworks,
            int[] includedUids,
            int[] excludedUids,
            int networkId) {
        mIsEnabled = isEnabled;
        mAllowFallbackToDefaultConnection = allowFallbackToDefaultConnection;
        mShouldBlockNonMatchingNetworks = shouldBlockNonMatchingNetworks;
        mIncludedUids = includedUids != null ? includedUids.clone() : new int[0];
        mExcludedUids = excludedUids != null ? excludedUids.clone() : new int[0];
        mNetworkId = networkId;
    }

    PreferentialNetworkServiceConfig(Parcel in) {
        mIsEnabled = in.readInt() != 0;
        mAllowFallbackToDefaultConnection = in.readInt() != 0;
        mShouldBlockNonMatchingNetworks = in.readInt() != 0;
        mNetworkId = in.readInt();
        int[] inc = in.createIntArray();
        mIncludedUids = inc != null ? inc : new int[0];
        int[] exc = in.createIntArray();
        mExcludedUids = exc != null ? exc : new int[0];
    }

    public boolean isEnabled() {
        return mIsEnabled;
    }

    public boolean isFallbackToDefaultConnectionAllowed() {
        return mAllowFallbackToDefaultConnection;
    }

    public boolean shouldBlockNonMatchingNetworks() {
        return mShouldBlockNonMatchingNetworks;
    }

    public int[] getIncludedUids() {
        return mIncludedUids.clone();
    }

    public int[] getExcludedUids() {
        return mExcludedUids.clone();
    }

    public int getNetworkId() {
        return mNetworkId;
    }

    @Override
    public String toString() {
        return "PreferentialNetworkServiceConfig{"
                + "mIsEnabled=" + mIsEnabled
                + ", mAllowFallbackToDefaultConnection=" + mAllowFallbackToDefaultConnection
                + ", mShouldBlockNonMatchingNetworks=" + mShouldBlockNonMatchingNetworks
                + ", mNetworkId=" + mNetworkId
                + ", mIncludedUids=" + Arrays.toString(mIncludedUids)
                + ", mExcludedUids=" + Arrays.toString(mExcludedUids)
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PreferentialNetworkServiceConfig)) return false;
        PreferentialNetworkServiceConfig that = (PreferentialNetworkServiceConfig) o;
        return mIsEnabled == that.mIsEnabled
                && mAllowFallbackToDefaultConnection == that.mAllowFallbackToDefaultConnection
                && mShouldBlockNonMatchingNetworks == that.mShouldBlockNonMatchingNetworks
                && mNetworkId == that.mNetworkId
                && Arrays.equals(mIncludedUids, that.mIncludedUids)
                && Arrays.equals(mExcludedUids, that.mExcludedUids);
    }

    @Override
    public int hashCode() {
        int h = Objects.hash(
                Boolean.valueOf(mIsEnabled),
                Boolean.valueOf(mAllowFallbackToDefaultConnection),
                Boolean.valueOf(mShouldBlockNonMatchingNetworks),
                Integer.valueOf(mNetworkId));
        h = 31 * h + Arrays.hashCode(mIncludedUids);
        return 31 * h + Arrays.hashCode(mExcludedUids);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mIsEnabled ? 1 : 0);
        dest.writeInt(mAllowFallbackToDefaultConnection ? 1 : 0);
        dest.writeInt(mShouldBlockNonMatchingNetworks ? 1 : 0);
        dest.writeInt(mNetworkId);
        dest.writeIntArray(mIncludedUids);
        dest.writeIntArray(mExcludedUids);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<PreferentialNetworkServiceConfig> CREATOR =
            new Parcelable.Creator<PreferentialNetworkServiceConfig>() {
                @Override
                public PreferentialNetworkServiceConfig createFromParcel(Parcel in) {
                    return new PreferentialNetworkServiceConfig(in);
                }

                @Override
                public PreferentialNetworkServiceConfig[] newArray(int size) {
                    return new PreferentialNetworkServiceConfig[size];
                }
            };

    public static final class Builder {
        boolean mIsEnabled = false;
        int mNetworkId = 0;
        boolean mAllowFallbackToDefaultConnection = true;
        boolean mShouldBlockNonMatchingNetworks = false;
        int[] mIncludedUids = new int[0];
        int[] mExcludedUids = new int[0];

        public Builder() {
        }

        public Builder setEnabled(boolean isEnabled) {
            mIsEnabled = isEnabled;
            return this;
        }

        public Builder setFallbackToDefaultConnectionAllowed(boolean allowFallbackToDefaultConnection) {
            mAllowFallbackToDefaultConnection = allowFallbackToDefaultConnection;
            return this;
        }

        public Builder setShouldBlockNonMatchingNetworks(boolean blockNonMatchingNetworks) {
            mShouldBlockNonMatchingNetworks = blockNonMatchingNetworks;
            return this;
        }

        public Builder setIncludedUids(int[] uids) {
            Objects.requireNonNull(uids);
            mIncludedUids = uids.clone();
            return this;
        }

        public Builder setExcludedUids(int[] uids) {
            Objects.requireNonNull(uids);
            mExcludedUids = uids.clone();
            return this;
        }

        public Builder setNetworkId(int preferenceId) {
            if (preferenceId < PREFERENTIAL_NETWORK_ID_1 || preferenceId > PREFERENTIAL_NETWORK_ID_5) {
                throw new IllegalArgumentException("Invalid preferenceId: " + preferenceId);
            }
            mNetworkId = preferenceId;
            return this;
        }

        public PreferentialNetworkServiceConfig build() {
            if (mIncludedUids.length > 0 && mExcludedUids.length > 0) {
                throw new IllegalStateException(
                        "Both includedUids and excludedUids cannot be nonempty");
            }
            if (mShouldBlockNonMatchingNetworks && mAllowFallbackToDefaultConnection) {
                throw new IllegalStateException(
                        "A config cannot both allow fallback and block non-matching networks");
            }
            return new PreferentialNetworkServiceConfig(
                    mIsEnabled,
                    mAllowFallbackToDefaultConnection,
                    mShouldBlockNonMatchingNetworks,
                    mIncludedUids,
                    mExcludedUids,
                    mNetworkId);
        }
    }
}
