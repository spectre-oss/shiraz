// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

/** Typed binary digest value for APK integrity verification. */
public final class Checksum implements Parcelable {
    public static final int TYPE_WHOLE_MERKLE_ROOT_4K_SHA256 = 0x00000001;
    public static final int TYPE_WHOLE_MD5 = 0x00000002;
    public static final int TYPE_WHOLE_SHA1 = 0x00000004;
    public static final int TYPE_WHOLE_SHA256 = 0x00000008;
    public static final int TYPE_WHOLE_SHA512 = 0x00000010;
    public static final int TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256 = 0x00000020;
    public static final int TYPE_PARTIAL_MERKLE_ROOT_1M_SHA512 = 0x00000040;

    private final int mType;
    private final byte[] mValue;

    public Checksum(int type, byte[] value) {
        Objects.requireNonNull(value, "value must not be null");
        mType = type;
        mValue = value.clone();
    }

    Checksum(Parcel in) {
        mType = in.readInt();
        byte[] v = in.createByteArray();
        mValue = v != null ? v : new byte[0];
    }

    public int getType() {
        return mType;
    }

    public byte[] getValue() {
        return mValue.clone();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeByteArray(mValue);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "Checksum{type=" + mType + ", len=" + mValue.length + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Checksum)) return false;
        Checksum that = (Checksum) o;
        return mType == that.mType && Arrays.equals(mValue, that.mValue);
    }

    @Override
    public int hashCode() {
        return 31 * mType + Arrays.hashCode(mValue);
    }

    public static final Creator<Checksum> CREATOR = new Creator<Checksum>() {
        @Override
        public Checksum createFromParcel(Parcel in) {
            return new Checksum(in);
        }

        @Override
        public Checksum[] newArray(int size) {
            return new Checksum[size];
        }
    };
}
