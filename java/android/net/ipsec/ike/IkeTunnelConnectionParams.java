// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import java.util.Objects;

/** Parameters for establishing an IKEv2/IPsec tunnel-mode connection (completed in Batch 3). */
public final class IkeTunnelConnectionParams {
    private final IkeSessionParams mIkeParams;
    private final TunnelModeChildSessionParams mChildParams;

    public IkeTunnelConnectionParams(
            IkeSessionParams ikeParams, TunnelModeChildSessionParams childParams) {
        this.mIkeParams = Objects.requireNonNull(ikeParams, "ikeParams");
        this.mChildParams = Objects.requireNonNull(childParams, "childParams");
    }

    public IkeSessionParams getIkeSessionParams() {
        return mIkeParams;
    }

    public TunnelModeChildSessionParams getTunnelModeChildSessionParams() {
        return mChildParams;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mIkeParams, mChildParams);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IkeTunnelConnectionParams)) {
            return false;
        }
        IkeTunnelConnectionParams other = (IkeTunnelConnectionParams) o;
        return Objects.equals(mIkeParams, other.mIkeParams)
                && Objects.equals(mChildParams, other.mChildParams);
    }
}
