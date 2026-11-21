// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class BluetoothCodecType implements Parcelable {
    public static final long CODEC_ID_SBC = 0L;
    public static final long CODEC_ID_AAC = 2L;
    public static final long CODEC_ID_APTX = 16797695L;
    public static final long CODEC_ID_APTX_HD = 604035071L;
    public static final long CODEC_ID_LDAC = -1442763265L;
    public static final long CODEC_ID_OPUS = 16834815L;

    private final int mNativeCodecType;
    private final long mCodecId;
    private final String mCodecName;

    BluetoothCodecType(int codecType, long codecId, String codecName) {
        mNativeCodecType = codecType;
        mCodecId = codecId;
        mCodecName = codecName != null ? codecName : "UNKNOWN";
    }

    static BluetoothCodecType createFromType(int codecType) {
        switch (codecType) {
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC:
                return new BluetoothCodecType(codecType, CODEC_ID_SBC, "SBC");
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC:
                return new BluetoothCodecType(codecType, CODEC_ID_AAC, "AAC");
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX:
                return new BluetoothCodecType(codecType, CODEC_ID_APTX, "aptX");
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD:
                return new BluetoothCodecType(codecType, CODEC_ID_APTX_HD, "aptX HD");
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC:
                return new BluetoothCodecType(codecType, CODEC_ID_LDAC, "LDAC");
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_LC3:
                return new BluetoothCodecType(codecType, 3L, "LC3");
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_OPUS:
                return new BluetoothCodecType(codecType, CODEC_ID_OPUS, "Opus");
            default:
                return null;
        }
    }

    int getNativeCodecType() {
        return mNativeCodecType;
    }

    public boolean isMandatoryCodec() {
        return mCodecId == CODEC_ID_SBC;
    }

    public long getCodecId() {
        return mCodecId;
    }

    public String getCodecName() {
        return mCodecName;
    }

    public String toString() {
        return mCodecName;
    }

    public int hashCode() {
        return Long.hashCode(mCodecId);
    }

    public boolean equals(Object o) {
        if (o instanceof BluetoothCodecType) {
            return ((BluetoothCodecType) o).mCodecId == mCodecId;
        }
        return false;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mNativeCodecType);
        dest.writeLong(mCodecId);
        dest.writeString(mCodecName);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<BluetoothCodecType> CREATOR = new Parcelable.Creator<BluetoothCodecType>() {
        public BluetoothCodecType createFromParcel(Parcel in) {
            int nativeType = in.readInt();
            long id = in.readLong();
            String name = in.readString();
            return new BluetoothCodecType(nativeType, id, name);
        }

        public BluetoothCodecType[] newArray(int size) {
            return new BluetoothCodecType[size];
        }
    };
}
