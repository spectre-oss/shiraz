// SPDX-License-Identifier: GPL-3.0-only
package android.system;

import java.net.SocketAddress;

/** Linux AF_VSOCK socket address corresponding to {@code struct sockaddr_vm}. */
public final class VmSocketAddress extends SocketAddress {
    private static final long serialVersionUID = 1L;
    private final int svmPort;
    private final int svmCid;

    public VmSocketAddress(int svmPort, int svmCid) {
        this.svmPort = svmPort;
        this.svmCid = svmCid;
    }

    public int getSvmPort() {
        return svmPort;
    }

    public int getSvmCid() {
        return svmCid;
    }
}
