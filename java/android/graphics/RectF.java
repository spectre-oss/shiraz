// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import android.os.Parcel;
import android.os.Parcelable;

/** Axis-aligned bounds; rendering transforms and parcel transport are separate. */
public class RectF implements Parcelable {
    public float left, top, right, bottom;
    public RectF() { }
    public RectF(float left, float top, float right, float bottom) { set(left, top, right, bottom); }
    public RectF(RectF source) { if (source != null) set(source); }
    public final boolean isEmpty() { return left >= right || top >= bottom; }
    public final float width() { return right - left; }
    public final float height() { return bottom - top; }
    public final float centerX() { return (left + right) * .5f; }
    public final float centerY() { return (top + bottom) * .5f; }
    public void setEmpty() { set(0, 0, 0, 0); }
    public void set(float left, float top, float right, float bottom) { this.left = left; this.top = top; this.right = right; this.bottom = bottom; }
    public void set(RectF source) { set(source.left, source.top, source.right, source.bottom); }
    public void offset(float dx, float dy) { left += dx; right += dx; top += dy; bottom += dy; }
    public void offsetTo(float x, float y) { right += x - left; bottom += y - top; left = x; top = y; }
    public void inset(float dx, float dy) { left += dx; top += dy; right -= dx; bottom -= dy; }
    public boolean contains(float x, float y) { return !isEmpty() && x >= left && x < right && y >= top && y < bottom; }
    public boolean contains(float l, float t, float r, float b) { return !isEmpty() && left <= l && top <= t && right >= r && bottom >= b; }
    public boolean contains(RectF other) { return contains(other.left, other.top, other.right, other.bottom); }
    public boolean intersects(float l, float t, float r, float b) { return left < r && l < right && top < b && t < bottom; }
    public static boolean intersects(RectF a, RectF b) { return a.intersects(b.left, b.top, b.right, b.bottom); }
    public boolean intersect(float l, float t, float r, float b) { if (!intersects(l, t, r, b)) return false; set(Math.max(left, l), Math.max(top, t), Math.min(right, r), Math.min(bottom, b)); return true; }
    public boolean intersect(RectF other) { return intersect(other.left, other.top, other.right, other.bottom); }
    public boolean setIntersect(RectF a, RectF b) { if (!intersects(a, b)) return false; set(Math.max(a.left, b.left), Math.max(a.top, b.top), Math.min(a.right, b.right), Math.min(a.bottom, b.bottom)); return true; }
    public void union(float l, float t, float r, float b) { if (l >= r || t >= b) return; if (isEmpty()) set(l, t, r, b); else set(Math.min(left, l), Math.min(top, t), Math.max(right, r), Math.max(bottom, b)); }
    public void union(RectF other) { union(other.left, other.top, other.right, other.bottom); }
    public void union(float x, float y) { left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y); }
    public void sort() { if (left > right) { float old = left; left = right; right = old; } if (top > bottom) { float old = top; top = bottom; bottom = old; } }
    @Override public boolean equals(Object other) { if (other == this) return true; if (other == null || other.getClass() != getClass()) return false; RectF value = (RectF)other; return Float.compare(left, value.left) == 0 && Float.compare(top, value.top) == 0 && Float.compare(right, value.right) == 0 && Float.compare(bottom, value.bottom) == 0; }
    public RectF(Rect source) { if (source != null) set(source); }
    public void set(Rect source) { set(source.left, source.top, source.right, source.bottom); }
    public void round(Rect out) { out.set(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom)); }
    public void roundOut(Rect out) { out.set((int)Math.floor(left), (int)Math.floor(top), (int)Math.ceil(right), (int)Math.ceil(bottom)); }
    @Override public int hashCode() { int hash = left == 0 ? 0 : Float.floatToIntBits(left); hash = 31 * hash + (top == 0 ? 0 : Float.floatToIntBits(top)); hash = 31 * hash + (right == 0 ? 0 : Float.floatToIntBits(right)); return 31 * hash + (bottom == 0 ? 0 : Float.floatToIntBits(bottom)); }
    @Override public String toString() { return "RectF(" + left + ", " + top + ", " + right + ", " + bottom + ")"; }
    public String toShortString() { return "[" + left + "," + top + "][" + right + "," + bottom + "]"; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { throw new UnsupportedOperationException("RectF parcel transport is not implemented"); }
    public void readFromParcel(Parcel in) { throw new UnsupportedOperationException("RectF parcel decoding is not implemented"); }
    public static final Parcelable.Creator<RectF> CREATOR = new Parcelable.Creator<RectF>() {
        public RectF createFromParcel(Parcel in) { throw new UnsupportedOperationException("RectF parcel decoding is not implemented"); }
        public RectF[] newArray(int size) { return new RectF[size]; }
    };
}
