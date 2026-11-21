// SPDX-License-Identifier: GPL-3.0-only
package android.companion;

import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.os.Parcel;
import android.os.Parcelable;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

public final class BluetoothLeDeviceFilter implements DeviceFilter<ScanResult> {
    private static final int RENAME_PREFIX_LENGTH_LIMIT = 10;

    private final Pattern mNamePattern;
    private final ScanFilter mScanFilter;
    private final byte[] mRawDataFilter;
    private final byte[] mRawDataFilterMask;
    private final String mRenamePrefix;
    private final String mRenameSuffix;
    private final int mRenameBytesFrom;
    private final int mRenameBytesLength;
    private final int mRenameNameFrom;
    private final int mRenameNameLength;
    private final boolean mRenameBytesReverseOrder;

    BluetoothLeDeviceFilter(Pattern namePattern, ScanFilter scanFilter,
            byte[] rawDataFilter, byte[] rawDataFilterMask, String renamePrefix,
            String renameSuffix, int renameBytesFrom, int renameBytesLength,
            int renameNameFrom, int renameNameLength, boolean renameBytesReverseOrder) {
        mNamePattern = namePattern;
        mScanFilter = scanFilter;
        mRawDataFilter = rawDataFilter;
        mRawDataFilterMask = rawDataFilterMask;
        mRenamePrefix = renamePrefix;
        mRenameSuffix = renameSuffix;
        mRenameBytesFrom = renameBytesFrom;
        mRenameBytesLength = renameBytesLength;
        mRenameNameFrom = renameNameFrom;
        mRenameNameLength = renameNameLength;
        mRenameBytesReverseOrder = renameBytesReverseOrder;
    }

    public static int getRenamePrefixLengthLimit() {
        return RENAME_PREFIX_LENGTH_LIMIT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BluetoothLeDeviceFilter)) return false;
        BluetoothLeDeviceFilter that = (BluetoothLeDeviceFilter) o;
        String p1 = mNamePattern != null ? mNamePattern.pattern() : null;
        String p2 = that.mNamePattern != null ? that.mNamePattern.pattern() : null;
        return mRenameBytesFrom == that.mRenameBytesFrom
                && mRenameBytesLength == that.mRenameBytesLength
                && mRenameNameFrom == that.mRenameNameFrom
                && mRenameNameLength == that.mRenameNameLength
                && mRenameBytesReverseOrder == that.mRenameBytesReverseOrder
                && Objects.equals(p1, p2)
                && Objects.equals(mScanFilter, that.mScanFilter)
                && Arrays.equals(mRawDataFilter, that.mRawDataFilter)
                && Arrays.equals(mRawDataFilterMask, that.mRawDataFilterMask)
                && Objects.equals(mRenamePrefix, that.mRenamePrefix)
                && Objects.equals(mRenameSuffix, that.mRenameSuffix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mNamePattern != null ? mNamePattern.pattern() : null,
                mScanFilter, Arrays.hashCode(mRawDataFilter),
                Arrays.hashCode(mRawDataFilterMask), mRenamePrefix, mRenameSuffix);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNamePattern != null ? mNamePattern.pattern() : null);
        dest.writeTypedObject(mScanFilter, flags);
        dest.writeByteArray(mRawDataFilter);
        dest.writeByteArray(mRawDataFilterMask);
        dest.writeString(mRenamePrefix);
        dest.writeString(mRenameSuffix);
        dest.writeInt(mRenameBytesFrom);
        dest.writeInt(mRenameBytesLength);
        dest.writeInt(mRenameNameFrom);
        dest.writeInt(mRenameNameLength);
        dest.writeBoolean(mRenameBytesReverseOrder);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "BluetoothLeDeviceFilter{namePattern=" + mNamePattern
                + ", scanFilter=" + mScanFilter + "}";
    }

    public static final Creator<BluetoothLeDeviceFilter> CREATOR = new Creator<BluetoothLeDeviceFilter>() {
        @Override
        public BluetoothLeDeviceFilter createFromParcel(Parcel in) {
            String pat = in.readString();
            ScanFilter sf = in.readTypedObject(ScanFilter.CREATOR);
            byte[] raw = in.createByteArray();
            byte[] mask = in.createByteArray();
            String prefix = in.readString();
            String suffix = in.readString();
            int bFrom = in.readInt();
            int bLen = in.readInt();
            int nFrom = in.readInt();
            int nLen = in.readInt();
            boolean rev = in.readBoolean();
            return new BluetoothLeDeviceFilter(
                    pat != null ? Pattern.compile(pat) : null,
                    sf, raw, mask, prefix, suffix, bFrom, bLen, nFrom, nLen, rev);
        }

        @Override
        public BluetoothLeDeviceFilter[] newArray(int size) {
            return new BluetoothLeDeviceFilter[size];
        }
    };

    public static final class Builder {
        private Pattern mNamePattern;
        private ScanFilter mScanFilter;
        private byte[] mRawDataFilter;
        private byte[] mRawDataFilterMask;
        private String mRenamePrefix;
        private String mRenameSuffix;
        private int mRenameBytesFrom = -1;
        private int mRenameBytesLength;
        private int mRenameNameFrom = -1;
        private int mRenameNameLength;
        private boolean mRenameBytesReverseOrder;

        public Builder() {
        }

        public Builder setNamePattern(Pattern regex) {
            mNamePattern = regex;
            return this;
        }

        public Builder setScanFilter(ScanFilter scanFilter) {
            mScanFilter = scanFilter;
            return this;
        }

        public Builder setRawDataFilter(byte[] rawDataFilter, byte[] rawDataFilterMask) {
            Objects.requireNonNull(rawDataFilter, "rawDataFilter must not be null");
            if (rawDataFilterMask != null && rawDataFilter.length != rawDataFilterMask.length) {
                throw new IllegalArgumentException(
                        "Mask and filter should be the same length");
            }
            mRawDataFilter = rawDataFilter.clone();
            mRawDataFilterMask = rawDataFilterMask != null ? rawDataFilterMask.clone() : null;
            return this;
        }

        public Builder setRenameFromBytes(String prefix, String suffix,
                int bytesFrom, int bytesLength, ByteOrder byteOrder) {
            checkRenamePrefix(prefix);
            mRenamePrefix = prefix;
            mRenameSuffix = suffix;
            mRenameBytesFrom = bytesFrom;
            mRenameBytesLength = bytesLength;
            mRenameBytesReverseOrder = byteOrder == ByteOrder.LITTLE_ENDIAN;
            return this;
        }

        public Builder setRenameFromName(String prefix, String suffix,
                int nameFrom, int nameLength) {
            checkRenamePrefix(prefix);
            mRenamePrefix = prefix;
            mRenameSuffix = suffix;
            mRenameNameFrom = nameFrom;
            mRenameNameLength = nameLength;
            return this;
        }

        private static void checkRenamePrefix(String prefix) {
            Objects.requireNonNull(prefix, "prefix must not be null");
            if (prefix.length() > RENAME_PREFIX_LENGTH_LIMIT) {
                throw new IllegalArgumentException("prefix is too long");
            }
        }

        public BluetoothLeDeviceFilter build() {
            return new BluetoothLeDeviceFilter(mNamePattern, mScanFilter,
                    mRawDataFilter, mRawDataFilterMask, mRenamePrefix, mRenameSuffix,
                    mRenameBytesFrom, mRenameBytesLength, mRenameNameFrom, mRenameNameLength,
                    mRenameBytesReverseOrder);
        }
    }
}
