// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth.le;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class TransportBlock implements Parcelable {
    private final int mOrgId;
    private final int mTdsFlags;
    private final int mTransportDataLength;
    private final byte[] mTransportData;

    public TransportBlock(int orgId, int tdsFlags, int transportDataLength, byte[] transportData) {
        mOrgId = orgId;
        mTdsFlags = tdsFlags;
        mTransportDataLength = transportDataLength;
        mTransportData = transportData != null ? transportData.clone() : null;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mOrgId);
        dest.writeInt(mTdsFlags);
        dest.writeInt(mTransportDataLength);
        dest.writeByteArray(mTransportData);
    }

    public int describeContents() {
        return 0;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TransportBlock)) {
            return false;
        }
        TransportBlock other = (TransportBlock) obj;
        return Arrays.equals(toByteArray(), other.toByteArray());
    }

    public int hashCode() {
        return Arrays.hashCode(toByteArray());
    }

    public static final Parcelable.Creator<TransportBlock> CREATOR = new Parcelable.Creator<TransportBlock>() {
        public TransportBlock createFromParcel(Parcel in) {
            int orgId = in.readInt();
            int tdsFlags = in.readInt();
            int length = in.readInt();
            byte[] data = in.createByteArray();
            return new TransportBlock(orgId, tdsFlags, length, data);
        }

        public TransportBlock[] newArray(int size) {
            return new TransportBlock[size];
        }
    };

    public int getOrgId() {
        return mOrgId;
    }

    public int getTdsFlags() {
        return mTdsFlags;
    }

    public int getTransportDataLength() {
        return mTransportDataLength;
    }

    public byte[] getTransportData() {
        return mTransportData != null ? mTransportData.clone() : null;
    }

    public byte[] toByteArray() {
        int dataLen = mTransportData != null ? mTransportData.length : 0;
        if (mTransportDataLength != dataLen) {
            return null;
        }
        byte[] out = new byte[totalBytes()];
        out[0] = (byte) (mOrgId & 0xFF);
        out[1] = (byte) (mTdsFlags & 0xFF);
        out[2] = (byte) (mTransportDataLength & 0xFF);
        if (mTransportData != null && dataLen > 0) {
            System.arraycopy(mTransportData, 0, out, 3, dataLen);
        }
        return out;
    }

    public int totalBytes() {
        return 3 + (mTransportData != null ? mTransportData.length : 0);
    }
}
