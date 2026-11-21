// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ChildSessionConfiguration {
    private final List<IkeTrafficSelector> mInboundTs;
    private final List<IkeTrafficSelector> mOutboundTs;

    ChildSessionConfiguration(
            List<IkeTrafficSelector> inboundTs, List<IkeTrafficSelector> outboundTs) {
        this.mInboundTs =
                Collections.unmodifiableList(new ArrayList<IkeTrafficSelector>(inboundTs));
        this.mOutboundTs =
                Collections.unmodifiableList(new ArrayList<IkeTrafficSelector>(outboundTs));
    }

    public List<IkeTrafficSelector> getInboundTrafficSelectors() {
        return mInboundTs;
    }

    public List<IkeTrafficSelector> getOutboundTrafficSelectors() {
        return mOutboundTs;
    }

    public static final class Builder {
        private final List<IkeTrafficSelector> mInboundTs;
        private final List<IkeTrafficSelector> mOutboundTs;

        public Builder(
                List<IkeTrafficSelector> inTs, List<IkeTrafficSelector> outTs) {
            Objects.requireNonNull(inTs, "inTs was null");
            Objects.requireNonNull(outTs, "outTs was null");
            if (inTs.isEmpty() || outTs.isEmpty()) {
                throw new IllegalArgumentException("Traffic selector list cannot be empty");
            }
            this.mInboundTs = new ArrayList<IkeTrafficSelector>(inTs);
            this.mOutboundTs = new ArrayList<IkeTrafficSelector>(outTs);
        }

        public ChildSessionConfiguration build() {
            return new ChildSessionConfiguration(mInboundTs, mOutboundTs);
        }
    }
}
