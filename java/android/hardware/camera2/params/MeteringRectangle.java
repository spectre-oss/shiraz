// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2.params;

import android.graphics.Point;
import android.graphics.Rect;
import android.util.Size;

public final class MeteringRectangle {
    public static final int METERING_WEIGHT_MIN = 0;
    public static final int METERING_WEIGHT_MAX = 1000;
    public static final int METERING_WEIGHT_DONT_CARE = 0;

    private final int mX;
    private final int mY;
    private final int mWidth;
    private final int mHeight;
    private final int mWeight;

    public MeteringRectangle(int x, int y, int width, int height, int meteringWeight) {
        if (x < 0) throw new IllegalArgumentException("x must be >= 0");
        if (y < 0) throw new IllegalArgumentException("y must be >= 0");
        if (width < 0) throw new IllegalArgumentException("width must be >= 0");
        if (height < 0) throw new IllegalArgumentException("height must be >= 0");
        if (meteringWeight < METERING_WEIGHT_MIN || meteringWeight > METERING_WEIGHT_MAX) {
            throw new IllegalArgumentException("meteringWeight out of range [0, 1000]");
        }
        mX = x;
        mY = y;
        mWidth = width;
        mHeight = height;
        mWeight = meteringWeight;
    }

    public MeteringRectangle(Point xy, Size dimensions, int meteringWeight) {
        this(requirePoint(xy).x, xy.y, requireSize(dimensions).getWidth(), dimensions.getHeight(), meteringWeight);
    }

    public MeteringRectangle(Rect rect, int meteringWeight) {
        this(requireRect(rect).left, rect.top, rect.width(), rect.height(), meteringWeight);
    }

    private static Point requirePoint(Point p) {
        if (p == null) throw new NullPointerException("xy must not be null");
        return p;
    }

    private static Size requireSize(Size s) {
        if (s == null) throw new NullPointerException("dimensions must not be null");
        return s;
    }

    private static Rect requireRect(Rect r) {
        if (r == null) throw new NullPointerException("rect must not be null");
        return r;
    }

    public int getX() {
        return mX;
    }

    public int getY() {
        return mY;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getMeteringWeight() {
        return mWeight;
    }

    public Point getUpperLeftPoint() {
        return new Point(mX, mY);
    }

    public Size getSize() {
        return new Size(mWidth, mHeight);
    }

    public Rect getRect() {
        return new Rect(mX, mY, mX + mWidth, mY + mHeight);
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof MeteringRectangle) && equals((MeteringRectangle) other);
    }

    public boolean equals(MeteringRectangle other) {
        if (other == null) return false;
        return mX == other.mX
                && mY == other.mY
                && mWidth == other.mWidth
                && mHeight == other.mHeight
                && mWeight == other.mWeight;
    }

    @Override
    public int hashCode() {
        int h = mX;
        h = 31 * h + mY;
        h = 31 * h + mWidth;
        h = 31 * h + mHeight;
        h = 31 * h + mWeight;
        return h;
    }

    @Override
    public String toString() {
        return String.format("(x:%d, y:%d, w:%d, h:%d, wt:%d)", mX, mY, mWidth, mHeight, mWeight);
    }
}
