// SPDX-License-Identifier: GPL-3.0-only
package android.system;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

/** Socket message header corresponding to POSIX {@code struct msghdr}. */
public final class StructMsghdr {
    public SocketAddress msg_name;
    public final ByteBuffer[] msg_iov;
    public StructCmsghdr[] msg_control;
    public int msg_flags;

    public StructMsghdr(
            SocketAddress msg_name,
            ByteBuffer[] msg_iov,
            StructCmsghdr[] msg_control,
            int msg_flags) {
        if (msg_iov == null) throw new NullPointerException("msg_iov == null");
        this.msg_name = msg_name;
        this.msg_iov = msg_iov;
        this.msg_control = msg_control;
        this.msg_flags = msg_flags;
    }
}
