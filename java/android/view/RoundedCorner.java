// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.graphics.Point;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class RoundedCorner implements Parcelable {
    public static final int POSITION_TOP_LEFT = 0;
    public static final int POSITION_TOP_RIGHT = 1;
    public static final int POSITION_BOTTOM_RIGHT = 2;
    public static final int POSITION_BOTTOM_LEFT = 3;

    private final int mPosition;
    private final int mRadius;
    private final Point mCenter;

    RoundedCorner(int position) {
        mPosition = position;
        mRadius = 0;
        mCenter = new Point(0, 0);
    }

    public RoundedCorner(int position, int radius, int centerX, int centerY) {
        mPosition = position;
        mRadius = radius;
        mCenter = new Point(centerX, centerY);
    }

    public int getPosition() {
        return mPosition;
    }

    public int getRadius() {
        return mRadius;
    }

    public Point getCenter() {
        return new Point(mCenter);
    }

    boolean isEmpty() {
        return mRadius <= 0 || mCenter.x <= 0 || mCenter.y <= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoundedCorner)) return false;
        RoundedCorner that = (RoundedCorner) o;
        return mPosition == that.mPosition
                && mRadius == that.mRadius
                && Objects.equals(mCenter, that.mCenter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPosition, mRadius, mCenter);
    }

    @Override
    public String toString() {
        return "RoundedCorner{position=" + mPosition
                + ", radius=" + mRadius
                + ", center=" + mCenter + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mPosition);
        out.writeInt(mRadius);
        out.writeInt(mCenter.x);
        out.writeInt(mCenter.y);
    }

    public static final Parcelable.Creator<RoundedCorner> CREATOR =
            new Parcelable.Creator<RoundedCorner>() {
                @Override
                public RoundedCorner createFromParcel(Parcel in) {
                    return new RoundedCorner(
                            in.readInt(),
                            in.readInt(),
                            in.readInt(),
                            in.readInt());
                }

                @Override
                public RoundedCorner[] newArray(int size) {
                    return new RoundedCorner[size];
                }
            };
}
