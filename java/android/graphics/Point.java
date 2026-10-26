// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import android.os.Parcel;
import android.os.Parcelable;
public class Point implements Parcelable {
    public int x, y;
    public Point() { }
    public Point(int x, int y) { set(x, y); }
    public Point(Point source) { this(source.x, source.y); }
    public void set(int x, int y) { this.x = x; this.y = y; }
    public final void negate() { x = -x; y = -y; }
    public final void offset(int dx, int dy) { x += dx; y += dy; }
    public final boolean equals(int x, int y) { return this.x == x && this.y == y; }
    @Override public boolean equals(Object other) { return other instanceof Point && equals(((Point)other).x, ((Point)other).y); }
    @Override public int hashCode() { return 31 * x + y; }
    @Override public String toString() { return "Point(" + x + ", " + y + ")"; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel destination, int flags) { throw new UnsupportedOperationException("Point parcel transport is not implemented"); }
    public static final Parcelable.Creator<Point> CREATOR = new Parcelable.Creator<Point>() {
        public Point createFromParcel(Parcel source) { throw new UnsupportedOperationException("Point parcel decoding is not implemented"); }
        public Point[] newArray(int size) { return new Point[size]; }
    };
}
