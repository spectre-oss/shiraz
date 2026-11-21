// SPDX-License-Identifier: GPL-3.0-only
package android.mtp;

import java.util.Objects;

public final class MtpObjectInfo {
    private int mHandle;
    private int mStorageId;
    private int mFormat;
    private int mProtectionStatus;
    private long mCompressedSize;
    private int mThumbFormat;
    private long mThumbCompressedSize;
    private long mThumbPixWidth;
    private long mThumbPixHeight;
    private long mImagePixWidth;
    private long mImagePixHeight;
    private long mImagePixDepth;
    private int mParent;
    private int mAssociationType;
    private int mAssociationDesc;
    private long mSequenceNumber;
    private String mName = "";
    private long mDateCreated;
    private long mDateModified;
    private String mKeywords = "";

    private MtpObjectInfo() {
    }

    private static int longToUint32(long value, String valueName) {
        if (value < 0L || value > 0xffffffffL) {
            throw new IllegalArgumentException(
                    valueName + " must be a 32-bit unsigned integer: " + value);
        }
        return (int) value;
    }

    private static int uint32ToInt(long value, String valueName) {
        if (value < 0L) {
            throw new IllegalStateException("Negative value for " + valueName);
        }
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    valueName + " exceeds Integer.MAX_VALUE: " + value);
        }
        return (int) value;
    }

    public int getObjectHandle() {
        return mHandle;
    }

    public int getStorageId() {
        return mStorageId;
    }

    public int getFormat() {
        return mFormat;
    }

    public int getProtectionStatus() {
        return mProtectionStatus;
    }

    public int getCompressedSize() {
        return uint32ToInt(mCompressedSize, "compressedSize");
    }

    public long getCompressedSizeLong() {
        return mCompressedSize;
    }

    public int getThumbFormat() {
        return mThumbFormat;
    }

    public int getThumbCompressedSize() {
        return uint32ToInt(mThumbCompressedSize, "thumbCompressedSize");
    }

    public long getThumbCompressedSizeLong() {
        return mThumbCompressedSize;
    }

    public int getThumbPixWidth() {
        return uint32ToInt(mThumbPixWidth, "thumbPixWidth");
    }

    public long getThumbPixWidthLong() {
        return mThumbPixWidth;
    }

    public int getThumbPixHeight() {
        return uint32ToInt(mThumbPixHeight, "thumbPixHeight");
    }

    public long getThumbPixHeightLong() {
        return mThumbPixHeight;
    }

    public int getImagePixWidth() {
        return uint32ToInt(mImagePixWidth, "imagePixWidth");
    }

    public long getImagePixWidthLong() {
        return mImagePixWidth;
    }

    public int getImagePixHeight() {
        return uint32ToInt(mImagePixHeight, "imagePixHeight");
    }

    public long getImagePixHeightLong() {
        return mImagePixHeight;
    }

    public int getImagePixDepth() {
        return uint32ToInt(mImagePixDepth, "imagePixDepth");
    }

    public long getImagePixDepthLong() {
        return mImagePixDepth;
    }

    public int getParent() {
        return mParent;
    }

    public int getAssociationType() {
        return mAssociationType;
    }

    public int getAssociationDesc() {
        return mAssociationDesc;
    }

    public int getSequenceNumber() {
        return uint32ToInt(mSequenceNumber, "sequenceNumber");
    }

    public long getSequenceNumberLong() {
        return mSequenceNumber;
    }

    public String getName() {
        return mName;
    }

    public long getDateCreated() {
        return mDateCreated;
    }

    public long getDateModified() {
        return mDateModified;
    }

    public String getKeywords() {
        return mKeywords;
    }

    public static class Builder {
        private MtpObjectInfo mObjectInfo;

        public Builder() {
            mObjectInfo = new MtpObjectInfo();
            mObjectInfo.mHandle = -1;
        }

        public Builder(MtpObjectInfo objectInfo) {
            Objects.requireNonNull(objectInfo, "objectInfo");
            mObjectInfo = new MtpObjectInfo();
            mObjectInfo.mHandle = -1;
            mObjectInfo.mAssociationDesc = objectInfo.mAssociationDesc;
            mObjectInfo.mAssociationType = objectInfo.mAssociationType;
            mObjectInfo.mCompressedSize = objectInfo.mCompressedSize;
            mObjectInfo.mDateCreated = objectInfo.mDateCreated;
            mObjectInfo.mDateModified = objectInfo.mDateModified;
            mObjectInfo.mFormat = objectInfo.mFormat;
            mObjectInfo.mImagePixDepth = objectInfo.mImagePixDepth;
            mObjectInfo.mImagePixHeight = objectInfo.mImagePixHeight;
            mObjectInfo.mImagePixWidth = objectInfo.mImagePixWidth;
            mObjectInfo.mKeywords = objectInfo.mKeywords;
            mObjectInfo.mName = objectInfo.mName;
            mObjectInfo.mParent = objectInfo.mParent;
            mObjectInfo.mProtectionStatus = objectInfo.mProtectionStatus;
            mObjectInfo.mSequenceNumber = objectInfo.mSequenceNumber;
            mObjectInfo.mStorageId = objectInfo.mStorageId;
            mObjectInfo.mThumbCompressedSize = objectInfo.mThumbCompressedSize;
            mObjectInfo.mThumbFormat = objectInfo.mThumbFormat;
            mObjectInfo.mThumbPixHeight = objectInfo.mThumbPixHeight;
            mObjectInfo.mThumbPixWidth = objectInfo.mThumbPixWidth;
        }

        public Builder setObjectHandle(int value) {
            mObjectInfo.mHandle = value;
            return this;
        }

        public Builder setAssociationDesc(int value) {
            mObjectInfo.mAssociationDesc = value;
            return this;
        }

        public Builder setAssociationType(int value) {
            mObjectInfo.mAssociationType = value;
            return this;
        }

        public Builder setCompressedSize(long value) {
            longToUint32(value, "value");
            mObjectInfo.mCompressedSize = value;
            return this;
        }

        public Builder setDateCreated(long value) {
            mObjectInfo.mDateCreated = value;
            return this;
        }

        public Builder setDateModified(long value) {
            mObjectInfo.mDateModified = value;
            return this;
        }

        public Builder setFormat(int value) {
            mObjectInfo.mFormat = value;
            return this;
        }

        public Builder setImagePixDepth(long value) {
            longToUint32(value, "value");
            mObjectInfo.mImagePixDepth = value;
            return this;
        }

        public Builder setImagePixHeight(long value) {
            longToUint32(value, "value");
            mObjectInfo.mImagePixHeight = value;
            return this;
        }

        public Builder setImagePixWidth(long value) {
            longToUint32(value, "value");
            mObjectInfo.mImagePixWidth = value;
            return this;
        }

        public Builder setKeywords(String value) {
            Objects.requireNonNull(value, "value");
            mObjectInfo.mKeywords = value;
            return this;
        }

        public Builder setName(String value) {
            Objects.requireNonNull(value, "value");
            mObjectInfo.mName = value;
            return this;
        }

        public Builder setParent(int value) {
            mObjectInfo.mParent = value;
            return this;
        }

        public Builder setProtectionStatus(int value) {
            mObjectInfo.mProtectionStatus = value;
            return this;
        }

        public Builder setSequenceNumber(long value) {
            longToUint32(value, "value");
            mObjectInfo.mSequenceNumber = value;
            return this;
        }

        public Builder setStorageId(int value) {
            mObjectInfo.mStorageId = value;
            return this;
        }

        public Builder setThumbCompressedSize(long value) {
            longToUint32(value, "value");
            mObjectInfo.mThumbCompressedSize = value;
            return this;
        }

        public Builder setThumbFormat(int value) {
            mObjectInfo.mThumbFormat = value;
            return this;
        }

        public Builder setThumbPixHeight(long value) {
            longToUint32(value, "value");
            mObjectInfo.mThumbPixHeight = value;
            return this;
        }

        public Builder setThumbPixWidth(long value) {
            longToUint32(value, "value");
            mObjectInfo.mThumbPixWidth = value;
            return this;
        }

        public MtpObjectInfo build() {
            MtpObjectInfo result = mObjectInfo;
            mObjectInfo = new Builder(result).mObjectInfo;
            mObjectInfo.mHandle = result.mHandle;
            return result;
        }
    }
}
