// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class QuicOptions {
    private final Set<String> mAllowedQuicHosts;
    private final Integer mInMemoryServerConfigsCacheSize;
    private final String mHandshakeUserAgent;
    private final Duration mIdleConnectionTimeout;

    QuicOptions(Builder builder) {
        this.mAllowedQuicHosts =
                Collections.unmodifiableSet(new LinkedHashSet<String>(builder.mAllowedQuicHosts));
        this.mInMemoryServerConfigsCacheSize = builder.mInMemoryServerConfigsCacheSize;
        this.mHandshakeUserAgent = builder.mHandshakeUserAgent;
        this.mIdleConnectionTimeout = builder.mIdleConnectionTimeout;
    }

    public Set<String> getAllowedQuicHosts() {
        return mAllowedQuicHosts;
    }

    public boolean hasInMemoryServerConfigsCacheSize() {
        return mInMemoryServerConfigsCacheSize != null;
    }

    public int getInMemoryServerConfigsCacheSize() {
        if (mInMemoryServerConfigsCacheSize == null) {
            throw new IllegalStateException("InMemoryServerConfigsCacheSize is not set");
        }
        return mInMemoryServerConfigsCacheSize.intValue();
    }

    public String getHandshakeUserAgent() {
        return mHandshakeUserAgent;
    }

    public Duration getIdleConnectionTimeout() {
        return mIdleConnectionTimeout;
    }

    public static final class Builder {
        final Set<String> mAllowedQuicHosts = new LinkedHashSet<String>();
        Integer mInMemoryServerConfigsCacheSize;
        String mHandshakeUserAgent;
        Duration mIdleConnectionTimeout;

        public Builder() {}

        public Builder addAllowedQuicHost(String quicHost) {
            mAllowedQuicHosts.add(Objects.requireNonNull(quicHost, "quicHost"));
            return this;
        }

        public Builder setInMemoryServerConfigsCacheSize(int maxServerConfigsStoredInProperties) {
            this.mInMemoryServerConfigsCacheSize =
                    Integer.valueOf(maxServerConfigsStoredInProperties);
            return this;
        }

        public Builder setHandshakeUserAgent(String handshakeUserAgent) {
            this.mHandshakeUserAgent = handshakeUserAgent;
            return this;
        }

        public Builder setIdleConnectionTimeout(Duration idleConnectionTimeout) {
            this.mIdleConnectionTimeout = idleConnectionTimeout;
            return this;
        }

        public QuicOptions build() {
            return new QuicOptions(this);
        }
    }
}
