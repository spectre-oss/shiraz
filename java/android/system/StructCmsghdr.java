// SPDX-License-Identifier: GPL-3.0-only
package android.system;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Ancillary control message header corresponding to POSIX {@code struct cmsghdr}. */
public final class StructCmsghdr {
    public final int cmsg_level;
    public final int cmsg_type;
    public final byte[] cmsg_data;

    public StructCmsghdr(int cmsg_level, int cmsg_type, short value) {
        ByteBuffer buffer = ByteBuffer.allocate(2).order(ByteOrder.nativeOrder());
        buffer.putShort(value);
        this.cmsg_level = cmsg_level;
        this.cmsg_type = cmsg_type;
        this.cmsg_data = buffer.array();
    }

    public StructCmsghdr(int cmsg_level, int cmsg_type, byte[] value) {
        if (value == null) throw new NullPointerException("cmsg_data == null");
        this.cmsg_level = cmsg_level;
        this.cmsg_type = cmsg_type;
        this.cmsg_data = value;
    }
}
