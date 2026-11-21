// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.cardemulation;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Locale;

public final class PollingFrame implements Parcelable {
    public static final int POLLING_LOOP_TYPE_A = 'A'; // 65
    public static final int POLLING_LOOP_TYPE_B = 'B'; // 66
    public static final int POLLING_LOOP_TYPE_F = 'F'; // 70
    public static final int POLLING_LOOP_TYPE_ON = 'O'; // 79
    public static final int POLLING_LOOP_TYPE_UNKNOWN = 'U'; // 85
    public static final int POLLING_LOOP_TYPE_OFF = 'X'; // 88

    private final int mType;
    private final byte[] mData;
    private final int mGain;
    private final long mTimestamp;
    private boolean mTriggeredAutoTransact;

    /** @hide */
    public PollingFrame(
            int type,
            byte[] data,
            int gain,
            long timestamp,
            boolean triggeredAutoTransact) {
        mType = type;
        mData = data != null ? data.clone() : new byte[0];
        mGain = gain;
        mTimestamp = timestamp;
        mTriggeredAutoTransact = triggeredAutoTransact;
    }

    private PollingFrame(Parcel in) {
        mType = in.readInt();
        byte[] data = in.createByteArray();
        mData = data != null ? data : new byte[0];
        mGain = in.readInt();
        mTimestamp = in.readLong();
        mTriggeredAutoTransact = in.readInt() != 0;
    }

    public int getType() {
        return mType;
    }

    public byte[] getData() {
        return mData.clone();
    }

    public int getVendorSpecificGain() {
        return mGain;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public boolean getTriggeredAutoTransact() {
        return mTriggeredAutoTransact;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeByteArray(mData);
        dest.writeInt(mGain);
        dest.writeLong(mTimestamp);
        dest.writeInt(mTriggeredAutoTransact ? 1 : 0);
    }

    @Override
    public String toString() {
        StringBuilder hex = new StringBuilder(mData.length * 2);
        for (int i = 0; i < mData.length; i++) {
            hex.append(String.format(Locale.US, "%02x", Integer.valueOf(mData[i] & 0xFF)));
        }
        return "PollingFrame { Type: " + (char) mType
                + ", gain: " + mGain
                + ", timestamp: " + Long.toUnsignedString(mTimestamp)
                + ", data: [" + hex + "] }";
    }

    public static final Parcelable.Creator<PollingFrame> CREATOR =
            new Parcelable.Creator<PollingFrame>() {
                @Override
                public PollingFrame createFromParcel(Parcel source) {
                    return new PollingFrame(source);
                }

                @Override
                public PollingFrame[] newArray(int size) {
                    return new PollingFrame[size];
                }
            };
}
