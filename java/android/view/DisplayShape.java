// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.graphics.Path;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class DisplayShape implements Parcelable {
    static final DisplayShape NONE = new DisplayShape("", 0, 0, 0f, 0);

    private final String mDisplayShapeSpec;
    private final int mDisplayWidth;
    private final int mDisplayHeight;
    private final float mPhysicalPixelDisplaySizeRatio;
    private final int mRotation;

    DisplayShape(String spec, int displayWidth, int displayHeight, float ratio, int rotation) {
        mDisplayShapeSpec = spec != null ? spec : "";
        mDisplayWidth = displayWidth;
        mDisplayHeight = displayHeight;
        mPhysicalPixelDisplaySizeRatio = ratio;
        mRotation = rotation;
    }

    public Path getPath() {
        Path path = new Path();
        if (mDisplayWidth > 0 && mDisplayHeight > 0) {
            path.addRect(0f, 0f, mDisplayWidth, mDisplayHeight, Path.Direction.CW);
        }
        return path;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDisplayShapeSpec, mDisplayWidth, mDisplayHeight,
                mPhysicalPixelDisplaySizeRatio, mRotation);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DisplayShape)) return false;
        DisplayShape that = (DisplayShape) o;
        return Objects.equals(mDisplayShapeSpec, that.mDisplayShapeSpec)
                && mDisplayWidth == that.mDisplayWidth
                && mDisplayHeight == that.mDisplayHeight
                && Float.compare(mPhysicalPixelDisplaySizeRatio, that.mPhysicalPixelDisplaySizeRatio) == 0
                && mRotation == that.mRotation;
    }

    @Override
    public String toString() {
        return "DisplayShape{spec=" + mDisplayShapeSpec
                + " displayWidth=" + mDisplayWidth
                + " displayHeight=" + mDisplayHeight
                + " physicalPixelDisplaySizeRatio=" + mPhysicalPixelDisplaySizeRatio
                + " rotation=" + mRotation + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDisplayShapeSpec);
        dest.writeInt(mDisplayWidth);
        dest.writeInt(mDisplayHeight);
        dest.writeFloat(mPhysicalPixelDisplaySizeRatio);
        dest.writeInt(mRotation);
    }

    public static final Parcelable.Creator<DisplayShape> CREATOR =
            new Parcelable.Creator<DisplayShape>() {
                @Override
                public DisplayShape createFromParcel(Parcel in) {
                    return new DisplayShape(
                            in.readString(),
                            in.readInt(),
                            in.readInt(),
                            in.readFloat(),
                            in.readInt());
                }

                @Override
                public DisplayShape[] newArray(int size) {
                    return new DisplayShape[size];
                }
            };
}
