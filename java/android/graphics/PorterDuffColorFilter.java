// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
/** Immutable constant-color source blended with the input pixel as destination. */
public class PorterDuffColorFilter extends ColorFilter {
    final int color;
    final PorterDuff.Mode mode;
    public PorterDuffColorFilter(int color,PorterDuff.Mode mode) {
        if (mode == null) throw new NullPointerException("mode");
        this.color=color; this.mode=mode;
    }
    public boolean equals(Object value) {
        if (value == null || value.getClass() != getClass()) return false;
        PorterDuffColorFilter other=(PorterDuffColorFilter)value; return color == other.color && mode == other.mode;
    }
    public int hashCode() { return 31*color+mode.hashCode(); }
}
