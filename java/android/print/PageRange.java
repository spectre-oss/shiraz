// SPDX-License-Identifier: GPL-3.0-only
package android.print;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class PageRange implements Parcelable {
    public static final PageRange ALL_PAGES = new PageRange(0, Integer.MAX_VALUE);

    private final int mStart;
    private final int mEnd;

    public PageRange(int start, int end) {
        if (start < 0) {
            throw new IllegalArgumentException("start cannot be less than zero.");
        }
        if (end < 0) {
            throw new IllegalArgumentException("end cannot be less than zero.");
        }
        if (start > end) {
            throw new IllegalArgumentException("start must be lesser than end.");
        }
        this.mStart = start;
        this.mEnd = end;
    }

    public int getStart() {
        return mStart;
    }

    public int getEnd() {
        return mEnd;
    }

    boolean contains(int pageIndex) {
        return pageIndex >= mStart && pageIndex <= mEnd;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        Objects.requireNonNull(parcel, "parcel");
        parcel.writeInt(mStart);
        parcel.writeInt(mEnd);
    }

    @Override
    public int hashCode() {
        int result = 31 + mEnd;
        return 31 * result + mStart;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PageRange other = (PageRange) obj;
        return mEnd == other.mEnd && mStart == other.mStart;
    }

    @Override
    public String toString() {
        if (mStart == 0 && mEnd == Integer.MAX_VALUE) {
            return "PageRange[<all pages>]";
        }
        return "PageRange[" + mStart + " - " + mEnd + "]";
    }

    public static final Parcelable.Creator<PageRange> CREATOR =
            new Parcelable.Creator<PageRange>() {
                @Override
                public PageRange createFromParcel(Parcel parcel) {
                    return new PageRange(parcel.readInt(), parcel.readInt());
                }

                @Override
                public PageRange[] newArray(int size) {
                    return new PageRange[size];
                }
            };
}
