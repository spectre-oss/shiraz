// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import android.os.Parcel;
import android.os.Parcelable;

/** Axis-aligned bounds; rendering transforms and parcel transport are separate. */
public final class Rect implements Parcelable {
    public int left, top, right, bottom;
    public Rect() { }
    public Rect(int left, int top, int right, int bottom) { set(left, top, right, bottom); }
    public Rect(Rect source) { if (source != null) set(source); }
    public final boolean isEmpty() { return left >= right || top >= bottom; }
    public final int width() { return right - left; }
    public final int height() { return bottom - top; }
    public final int centerX() { return (left + right) >> 1; }
    public final int centerY() { return (top + bottom) >> 1; }
    public void setEmpty() { set(0, 0, 0, 0); }
    public void set(int left, int top, int right, int bottom) { this.left = left; this.top = top; this.right = right; this.bottom = bottom; }
    public void set(Rect source) { set(source.left, source.top, source.right, source.bottom); }
    public void offset(int dx, int dy) { left += dx; right += dx; top += dy; bottom += dy; }
    public void offsetTo(int x, int y) { right += x - left; bottom += y - top; left = x; top = y; }
    public void inset(int dx, int dy) { left += dx; top += dy; right -= dx; bottom -= dy; }
    public boolean contains(int x, int y) { return !isEmpty() && x >= left && x < right && y >= top && y < bottom; }
    public boolean contains(int l, int t, int r, int b) { return !isEmpty() && left <= l && top <= t && right >= r && bottom >= b; }
    public boolean contains(Rect other) { return contains(other.left, other.top, other.right, other.bottom); }
    public boolean intersects(int l, int t, int r, int b) { return left < r && l < right && top < b && t < bottom; }
    public static boolean intersects(Rect a, Rect b) { return a.intersects(b.left, b.top, b.right, b.bottom); }
    public boolean intersect(int l, int t, int r, int b) { if (!intersects(l, t, r, b)) return false; set(Math.max(left, l), Math.max(top, t), Math.min(right, r), Math.min(bottom, b)); return true; }
    public boolean intersect(Rect other) { return intersect(other.left, other.top, other.right, other.bottom); }
    public boolean setIntersect(Rect a, Rect b) { if (!intersects(a, b)) return false; set(Math.max(a.left, b.left), Math.max(a.top, b.top), Math.min(a.right, b.right), Math.min(a.bottom, b.bottom)); return true; }
    public void union(int l, int t, int r, int b) { if (l >= r || t >= b) return; if (isEmpty()) set(l, t, r, b); else set(Math.min(left, l), Math.min(top, t), Math.max(right, r), Math.max(bottom, b)); }
    public void union(Rect other) { union(other.left, other.top, other.right, other.bottom); }
    public void union(int x, int y) { left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y); }
    public void sort() { if (left > right) { int old = left; left = right; right = old; } if (top > bottom) { int old = top; top = bottom; bottom = old; } }
    @Override public boolean equals(Object other) { if (other == this) return true; if (other == null || other.getClass() != getClass()) return false; Rect value = (Rect)other; return left == value.left && top == value.top && right == value.right && bottom == value.bottom; }
    public float exactCenterX() { return (left + right) * .5f; }
    public float exactCenterY() { return (top + bottom) * .5f; }
    public void inset(int l, int t, int r, int b) { left += l; top += t; right -= r; bottom -= b; }
    public String flattenToString() { return left + " " + top + " " + right + " " + bottom; }
    public static Rect unflattenFromString(String value) {
        if (value == null || value.length() > 256 || !value.matches("-?\\d+ -?\\d+ -?\\d+ -?\\d+")) return null;
        String[] fields = value.split(" "); try { return new Rect(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), Integer.parseInt(fields[3])); } catch (NumberFormatException error) { return null; }
    }
    @Override public int hashCode() { return 31 * (31 * (31 * left + top) + right) + bottom; }
    @Override public String toString() { return "Rect(" + left + ", " + top + ", " + right + ", " + bottom + ")"; }
    public String toShortString() { return "[" + left + "," + top + "][" + right + "," + bottom + "]"; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { throw new UnsupportedOperationException("Rect parcel transport is not implemented"); }
    public void readFromParcel(Parcel in) { throw new UnsupportedOperationException("Rect parcel decoding is not implemented"); }
    public static final Parcelable.Creator<Rect> CREATOR = new Parcelable.Creator<Rect>() {
        public Rect createFromParcel(Parcel in) { throw new UnsupportedOperationException("Rect parcel decoding is not implemented"); }
        public Rect[] newArray(int size) { return new Rect[size]; }
    };
}
