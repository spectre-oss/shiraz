// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth.le;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TransportDiscoveryData implements Parcelable {
    private final int mTransportDataType;
    private final List<TransportBlock> mTransportBlocks;

    public TransportDiscoveryData(int transportDataType, List<TransportBlock> transportBlocks) {
        mTransportDataType = transportDataType;
        mTransportBlocks = transportBlocks != null
                ? new ArrayList<TransportBlock>(transportBlocks)
                : new ArrayList<TransportBlock>();
    }

    public TransportDiscoveryData(byte[] transportDiscoveryData) {
        if (transportDiscoveryData == null || transportDiscoveryData.length < 1) {
            throw new IllegalArgumentException("Invalid transport discovery data");
        }
        mTransportDataType = transportDiscoveryData[0] & 0xFF;
        mTransportBlocks = new ArrayList<TransportBlock>();
        int offset = 1;
        while (offset < transportDiscoveryData.length) {
            if (offset + 3 > transportDiscoveryData.length) {
                throw new IllegalArgumentException("Truncated transport block header");
            }
            int orgId = transportDiscoveryData[offset] & 0xFF;
            int tdsFlags = transportDiscoveryData[offset + 1] & 0xFF;
            int dataLen = transportDiscoveryData[offset + 2] & 0xFF;
            offset += 3;
            if (offset + dataLen > transportDiscoveryData.length) {
                throw new IllegalArgumentException("Truncated transport block payload");
            }
            byte[] blockData = Arrays.copyOfRange(transportDiscoveryData, offset, offset + dataLen);
            offset += dataLen;
            mTransportBlocks.add(new TransportBlock(orgId, tdsFlags, dataLen, blockData));
        }
    }

    public int describeContents() {
        return 0;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TransportDiscoveryData)) {
            return false;
        }
        TransportDiscoveryData other = (TransportDiscoveryData) obj;
        return Arrays.equals(toByteArray(), other.toByteArray());
    }

    public int hashCode() {
        return Arrays.hashCode(toByteArray());
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mTransportDataType);
        dest.writeTypedList(mTransportBlocks);
    }

    public static final Parcelable.Creator<TransportDiscoveryData> CREATOR = new Parcelable.Creator<TransportDiscoveryData>() {
        public TransportDiscoveryData createFromParcel(Parcel in) {
            int dataType = in.readInt();
            List<TransportBlock> blocks = in.createTypedArrayList(TransportBlock.CREATOR);
            return new TransportDiscoveryData(dataType, blocks);
        }

        public TransportDiscoveryData[] newArray(int size) {
            return new TransportDiscoveryData[size];
        }
    };

    public int getTransportDataType() {
        return mTransportDataType;
    }

    public List<TransportBlock> getTransportBlocks() {
        return Collections.unmodifiableList(mTransportBlocks);
    }

    public byte[] toByteArray() {
        byte[] out = new byte[totalBytes()];
        out[0] = (byte) (mTransportDataType & 0xFF);
        int pos = 1;
        for (TransportBlock block : mTransportBlocks) {
            byte[] blockBytes = block.toByteArray();
            if (blockBytes == null) {
                return null;
            }
            System.arraycopy(blockBytes, 0, out, pos, blockBytes.length);
            pos += blockBytes.length;
        }
        return out;
    }

    public String toString() {
        byte[] bytes = toByteArray();
        if (bytes == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v).toUpperCase(java.util.Locale.ROOT));
        }
        return sb.toString();
    }

    public int totalBytes() {
        int size = 1;
        for (TransportBlock block : mTransportBlocks) {
            size += block.totalBytes();
        }
        return size;
    }
}
