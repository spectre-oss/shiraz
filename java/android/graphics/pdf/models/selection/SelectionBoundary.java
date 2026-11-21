// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf.models.selection;

import android.graphics.Point;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class SelectionBoundary implements Parcelable {
    private final int mIndex;
    private final Point mPoint;
    private final boolean mIsRtl;

    public SelectionBoundary(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Index cannot be negative");
        }
        this.mIndex = index;
        this.mPoint = null;
        this.mIsRtl = false;
    }

    public SelectionBoundary(Point point) {
        Objects.requireNonNull(point, "Point cannot be null");
        this.mIndex = -1;
        this.mPoint = new Point(point);
        this.mIsRtl = false;
    }

    private SelectionBoundary(int index, Point point, boolean isRtl) {
        this.mIndex = index;
        this.mPoint = point == null ? null : new Point(point);
        this.mIsRtl = isRtl;
    }

    public int getIndex() {
        return mIndex;
    }

    public Point getPoint() {
        return mPoint == null ? null : new Point(mPoint);
    }

    public boolean getIsRtl() {
        return mIsRtl;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeInt(mIndex);
        if (mPoint == null) {
            dest.writeInt(0);
        } else {
            dest.writeInt(1);
            dest.writeInt(mPoint.x);
            dest.writeInt(mPoint.y);
        }
        dest.writeBoolean(mIsRtl);
    }

    public static final Parcelable.Creator<SelectionBoundary> CREATOR =
            new Parcelable.Creator<SelectionBoundary>() {
                @Override
                public SelectionBoundary createFromParcel(Parcel in) {
                    int index = in.readInt();
                    Point point = null;
                    if (in.readInt() != 0) {
                        point = new Point(in.readInt(), in.readInt());
                    }
                    boolean isRtl = in.readBoolean();
                    return new SelectionBoundary(index, point, isRtl);
                }

                @Override
                public SelectionBoundary[] newArray(int size) {
                    return new SelectionBoundary[size];
                }
            };
}
