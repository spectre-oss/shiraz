// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

import java.time.Duration;

public final class DnsOptions {
    public static final int DNS_OPTION_UNSPECIFIED = 0;
    public static final int DNS_OPTION_ENABLED = 1;
    public static final int DNS_OPTION_DISABLED = 2;

    private final int mUseHttpStackDnsResolver;
    private final int mPersistHostCache;
    private final int mStaleDns;
    private final Duration mPersistHostCachePeriod;
    private final int mPreestablishConnectionsToStaleDnsResults;
    private final StaleDnsOptions mStaleDnsOptions;

    DnsOptions(Builder builder) {
        this.mUseHttpStackDnsResolver = builder.mUseHttpStackDnsResolver;
        this.mPersistHostCache = builder.mPersistHostCache;
        this.mStaleDns = builder.mStaleDns;
        this.mPersistHostCachePeriod = builder.mPersistHostCachePeriod;
        this.mPreestablishConnectionsToStaleDnsResults =
                builder.mPreestablishConnectionsToStaleDnsResults;
        this.mStaleDnsOptions = builder.mStaleDnsOptions;
    }

    public int getUseHttpStackDnsResolver() {
        return mUseHttpStackDnsResolver;
    }

    public int getPersistHostCache() {
        return mPersistHostCache;
    }

    public int getStaleDns() {
        return mStaleDns;
    }

    public Duration getPersistHostCachePeriod() {
        return mPersistHostCachePeriod;
    }

    public int getPreestablishConnectionsToStaleDnsResults() {
        return mPreestablishConnectionsToStaleDnsResults;
    }

    public StaleDnsOptions getStaleDnsOptions() {
        return mStaleDnsOptions;
    }

    public static class StaleDnsOptions {
        private final Duration mFreshLookupTimeout;
        private final Duration mMaxExpiredDelay;
        private final int mAllowCrossNetworkUsage;
        private final int mUseStaleOnNameNotResolved;

        StaleDnsOptions(Builder builder) {
            this.mFreshLookupTimeout = builder.mFreshLookupTimeout;
            this.mMaxExpiredDelay = builder.mMaxExpiredDelay;
            this.mAllowCrossNetworkUsage = builder.mAllowCrossNetworkUsage;
            this.mUseStaleOnNameNotResolved = builder.mUseStaleOnNameNotResolved;
        }

        public Duration getFreshLookupTimeout() {
            return mFreshLookupTimeout;
        }

        public Duration getMaxExpiredDelay() {
            return mMaxExpiredDelay;
        }

        public int getAllowCrossNetworkUsage() {
            return mAllowCrossNetworkUsage;
        }

        public int getUseStaleOnNameNotResolved() {
            return mUseStaleOnNameNotResolved;
        }

        public static final class Builder {
            Duration mFreshLookupTimeout;
            Duration mMaxExpiredDelay;
            int mAllowCrossNetworkUsage = DNS_OPTION_UNSPECIFIED;
            int mUseStaleOnNameNotResolved = DNS_OPTION_UNSPECIFIED;

            public Builder() {}

            public Builder setFreshLookupTimeout(Duration freshLookupTimeout) {
                this.mFreshLookupTimeout = freshLookupTimeout;
                return this;
            }

            public Builder setMaxExpiredDelay(Duration maxExpiredDelay) {
                this.mMaxExpiredDelay = maxExpiredDelay;
                return this;
            }

            public Builder setAllowCrossNetworkUsage(int allowCrossNetworkUsage) {
                this.mAllowCrossNetworkUsage = allowCrossNetworkUsage;
                return this;
            }

            public Builder setUseStaleOnNameNotResolved(int useStaleOnNameNotResolved) {
                this.mUseStaleOnNameNotResolved = useStaleOnNameNotResolved;
                return this;
            }

            public StaleDnsOptions build() {
                return new StaleDnsOptions(this);
            }
        }
    }

    public static final class Builder {
        int mUseHttpStackDnsResolver = DNS_OPTION_UNSPECIFIED;
        int mPersistHostCache = DNS_OPTION_UNSPECIFIED;
        int mStaleDns = DNS_OPTION_UNSPECIFIED;
        Duration mPersistHostCachePeriod;
        int mPreestablishConnectionsToStaleDnsResults = DNS_OPTION_UNSPECIFIED;
        StaleDnsOptions mStaleDnsOptions;

        public Builder() {}

        public Builder setUseHttpStackDnsResolver(int state) {
            this.mUseHttpStackDnsResolver = state;
            return this;
        }

        public Builder setStaleDns(int state) {
            this.mStaleDns = state;
            return this;
        }

        public Builder setStaleDnsOptions(StaleDnsOptions staleDnsOptions) {
            this.mStaleDnsOptions = staleDnsOptions;
            return this;
        }

        public Builder setPreestablishConnectionsToStaleDnsResults(int state) {
            this.mPreestablishConnectionsToStaleDnsResults = state;
            return this;
        }

        public Builder setPersistHostCache(int state) {
            this.mPersistHostCache = state;
            return this;
        }

        public Builder setPersistHostCachePeriod(Duration persistHostCachePeriod) {
            this.mPersistHostCachePeriod = persistHostCachePeriod;
            return this;
        }

        public DnsOptions build() {
            return new DnsOptions(this);
        }
    }
}
