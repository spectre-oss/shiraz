// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import android.os.Parcel;
import android.os.Parcelable;

/** Floating-point coordinates; parcel transport remains explicit unsupported work. */
public class PointF implements Parcelable {
    public float x, y;
    public PointF() { }
    public PointF(float x, float y) { set(x, y); }
    public PointF(Point point) { this(point.x, point.y); }
    public PointF(PointF point) { this(point.x, point.y); }
    public final void set(float x, float y) { this.x = x; this.y = y; }
    public final void set(PointF point) { set(point.x, point.y); }
    public final void negate() { x = -x; y = -y; }
    public final void offset(float dx, float dy) { x += dx; y += dy; }
    public final boolean equals(float x, float y) { return this.x == x && this.y == y; }
    @Override public boolean equals(Object other) {
        if (other == this) return true;
        if (other == null || other.getClass() != getClass()) return false;
        PointF point = (PointF)other;
        return Float.compare(x, point.x) == 0 && Float.compare(y, point.y) == 0;
    }
    @Override public int hashCode() { return 31 * (x == 0 ? 0 : Float.floatToIntBits(x)) + (y == 0 ? 0 : Float.floatToIntBits(y)); }
    public final float length() { return length(x, y); }
    public static float length(float x, float y) { return (float)Math.hypot(x, y); }
    @Override public String toString() { return "PointF(" + x + ", " + y + ")"; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { throw new UnsupportedOperationException("PointF parcel transport is not implemented"); }
    public void readFromParcel(Parcel in) { throw new UnsupportedOperationException("PointF parcel decoding is not implemented"); }
    public static final Parcelable.Creator<PointF> CREATOR = new Parcelable.Creator<PointF>() {
        public PointF createFromParcel(Parcel in) { throw new UnsupportedOperationException("PointF parcel decoding is not implemented"); }
        public PointF[] newArray(int size) { return new PointF[size]; }
    };
}
