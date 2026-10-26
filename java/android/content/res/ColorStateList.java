// SPDX-License-Identifier: GPL-3.0-only
package android.content.res;
import android.os.Parcelable;
import android.os.Parcel;

/** Ordered positive/negative state specifications with straight ARGB colors. */
public class ColorStateList implements Parcelable {
    private final int[][] states;
    private final int[] colors;
    private final int defaultColor;
    private final int[][] deferred;
    public ColorStateList(int[][] states, int[] colors) { this(states, colors, null); }
    ColorStateList(int[][] states, int[] colors, int[][] deferred) {
        this.deferred = deferred;
        if (states == null || colors == null) throw new NullPointerException("Color state arrays");
        if (states.length != colors.length || colors.length == 0 || colors.length > 4096) throw new IllegalArgumentException("Invalid color state count");
        this.states = new int[states.length][]; this.colors = colors.clone();
        int fallback = colors[0];
        for (int i = 0; i < states.length; ++i) {
            if (states[i] == null || states[i].length > 64) throw new IllegalArgumentException("Invalid color state specification");
            this.states[i] = states[i].clone();
            if (states[i].length == 0) fallback = colors[i];
        }
        defaultColor = fallback;
    }
    public static ColorStateList valueOf(int color) { return new ColorStateList(new int[][]{new int[0]}, new int[]{color}); }
    public int getDefaultColor() { return defaultColor; }
    public boolean isStateful() { return states[0].length > 0; }
    public boolean isOpaque() { for (int color : colors) if ((color >>> 24) != 255) return false; return true; }
    public boolean canApplyTheme() { return deferred != null; }
    public ColorStateList obtainForTheme(Resources.Theme theme) {
        if (theme == null || deferred == null) return this;
        int[] updated = colors.clone(); int[][] pending = new int[colors.length][]; boolean remains = false;
        for (int i = 0; i < colors.length; i++) {
            if (deferred[i] == null) continue;
            int[] entry = deferred[i].clone(); android.util.TypedValue value = new android.util.TypedValue();
            if (entry[0] != 0 && theme.resolveAttribute(entry[0], value, true) && value.type != android.util.TypedValue.TYPE_ATTRIBUTE) {
                if (value.type >= 28 && value.type <= 31) entry[2] = value.data;
                else if (value.resourceId != 0) entry[2] = theme.getResources().getColor(value.resourceId, theme);
                else throw new Resources.NotFoundException("Theme attribute is not a color");
                entry[0] = 0;
            }
            if (entry[1] != 0 && theme.resolveAttribute(entry[1], value, true) && value.type != android.util.TypedValue.TYPE_ATTRIBUTE) {
                float alpha;
                if (value.type == android.util.TypedValue.TYPE_FLOAT) alpha = Float.intBitsToFloat(value.data);
                else if (value.type == android.util.TypedValue.TYPE_INT_DEC) alpha = value.data;
                else throw new Resources.NotFoundException("Theme attribute is not an alpha");
                if (!Float.isFinite(alpha)) throw new Resources.NotFoundException("Nonfinite selector alpha");
                entry[3] = Float.floatToIntBits(alpha); entry[1] = 0;
            }
            int opacity = Math.round(Math.max(0, Math.min(255, (entry[2] >>> 24) * Float.intBitsToFloat(entry[3]))));
            updated[i] = (entry[2] & 0xffffff) | (opacity << 24);
            if (entry[0] != 0 || entry[1] != 0) { pending[i] = entry; remains = true; }
        }
        return new ColorStateList(states, updated, remains ? pending : null);
    }
    public int getColorForState(int[] stateSet, int fallback) {
        for (int i = 0; i < states.length; ++i) if (matches(states[i], stateSet)) return colors[i];
        return fallback;
    }
    private static boolean matches(int[] specification, int[] stateSet) {
        for (int value : specification) {
            if (value == 0) return true;
            boolean present = false; int id = value < 0 ? -value : value;
            if (stateSet != null) for (int state : stateSet) { if (state == 0) break; if (state == id) { present = true; break; } }
            if ((value > 0) != present) return false;
        }
        return true;
    }
    public ColorStateList withAlpha(int alpha) {
        if (alpha < 0 || alpha > 255) throw new IllegalArgumentException("Alpha outside 0..255");
        int[] modified = colors.clone();
        for (int i = 0; i < modified.length; ++i) modified[i] = (modified[i] & 0xffffff) | (alpha << 24);
        return new ColorStateList(states, modified);
    }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { throw new UnsupportedOperationException("ColorStateList parcel transport is not implemented"); }
}
