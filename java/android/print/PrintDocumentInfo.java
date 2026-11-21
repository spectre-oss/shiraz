// SPDX-License-Identifier: GPL-3.0-only
package android.print;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class PrintDocumentInfo implements Parcelable {
    public static final int PAGE_COUNT_UNKNOWN = -1;

    public static final int CONTENT_TYPE_UNKNOWN = -1;
    public static final int CONTENT_TYPE_DOCUMENT = 0;
    public static final int CONTENT_TYPE_PHOTO = 1;

    private String mName;
    private int mPageCount;
    private int mContentType;
    private long mDataSize;

    private PrintDocumentInfo() {
    }

    private PrintDocumentInfo(PrintDocumentInfo prototype) {
        this.mName = prototype.mName;
        this.mPageCount = prototype.mPageCount;
        this.mContentType = prototype.mContentType;
        this.mDataSize = prototype.mDataSize;
    }

    private PrintDocumentInfo(Parcel parcel) {
        this.mName = parcel.readString();
        this.mPageCount = parcel.readInt();
        this.mContentType = parcel.readInt();
        this.mDataSize = parcel.readLong();
    }

    public String getName() {
        return mName;
    }

    public int getPageCount() {
        return mPageCount;
    }

    public int getContentType() {
        return mContentType;
    }

    public long getDataSize() {
        return mDataSize;
    }

    void setDataSize(long dataSize) {
        this.mDataSize = dataSize;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        Objects.requireNonNull(parcel, "parcel");
        parcel.writeString(mName);
        parcel.writeInt(mPageCount);
        parcel.writeInt(mContentType);
        parcel.writeLong(mDataSize);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + (mName == null ? 0 : mName.hashCode());
        result = 31 * result + mContentType;
        result = 31 * result + mPageCount;
        result = 31 * result + (int) mDataSize;
        result = 31 * result + (int) (mDataSize >>> 32);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PrintDocumentInfo other = (PrintDocumentInfo) obj;
        return Objects.equals(mName, other.mName)
                && mContentType == other.mContentType
                && mPageCount == other.mPageCount
                && mDataSize == other.mDataSize;
    }

    @Override
    public String toString() {
        return "PrintDocumentInfo{name=" + mName
                + ", pageCount=" + mPageCount
                + ", contentType=" + mContentType
                + ", dataSize=" + mDataSize + "}";
    }

    public static final Parcelable.Creator<PrintDocumentInfo> CREATOR =
            new Parcelable.Creator<PrintDocumentInfo>() {
                @Override
                public PrintDocumentInfo createFromParcel(Parcel parcel) {
                    return new PrintDocumentInfo(parcel);
                }

                @Override
                public PrintDocumentInfo[] newArray(int size) {
                    return new PrintDocumentInfo[size];
                }
            };

    public static final class Builder {
        private final PrintDocumentInfo mPrototype;

        public Builder(String name) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("name cannot be empty");
            }
            mPrototype = new PrintDocumentInfo();
            mPrototype.mName = name;
            mPrototype.mPageCount = PAGE_COUNT_UNKNOWN;
            mPrototype.mContentType = CONTENT_TYPE_DOCUMENT;
        }

        public Builder setPageCount(int pageCount) {
            if (pageCount < 0 && pageCount != PAGE_COUNT_UNKNOWN) {
                throw new IllegalArgumentException(
                        "pageCount must be greater than or equal to zero or PAGE_COUNT_UNKNOWN");
            }
            mPrototype.mPageCount = pageCount;
            return this;
        }

        public Builder setContentType(int type) {
            if (type != CONTENT_TYPE_DOCUMENT
                    && type != CONTENT_TYPE_PHOTO
                    && type != CONTENT_TYPE_UNKNOWN) {
                throw new IllegalArgumentException("Invalid content type: " + type);
            }
            mPrototype.mContentType = type;
            return this;
        }

        public PrintDocumentInfo build() {
            if (mPrototype.mPageCount == 0) {
                mPrototype.mPageCount = PAGE_COUNT_UNKNOWN;
            }
            return new PrintDocumentInfo(mPrototype);
        }
    }
}
