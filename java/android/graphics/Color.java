// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Packed and object sRGB colors. Wide gamut and color-long APIs are not implemented. */
public class Color {
    public static final int BLACK = 0xff000000, DKGRAY = 0xff444444, GRAY = 0xff888888,
        LTGRAY = 0xffcccccc, WHITE = 0xffffffff, RED = 0xffff0000, GREEN = 0xff00ff00,
        BLUE = 0xff0000ff, YELLOW = 0xffffff00, CYAN = 0xff00ffff, MAGENTA = 0xffff00ff,
        TRANSPARENT = 0;
    private static final Map<String, Integer> NAMES = new HashMap<String, Integer>();
    static {
        String[] names = {"black", "darkgray", "gray", "lightgray", "white", "red", "green",
            "blue", "yellow", "cyan", "magenta", "aqua", "fuchsia", "darkgrey", "grey",
            "lightgrey", "lime", "maroon", "navy", "olive", "purple", "silver", "teal"};
        int[] colors = {BLACK, DKGRAY, GRAY, LTGRAY, WHITE, RED, GREEN, BLUE, YELLOW, CYAN,
            MAGENTA, CYAN, MAGENTA, DKGRAY, GRAY, LTGRAY, GREEN, 0xff800000, 0xff000080,
            0xff808000, 0xff800080, 0xffc0c0c0, 0xff008080};
        for (int i = 0; i < names.length; ++i) NAMES.put(names[i], colors[i]);
    }
    private final int value;
    public Color() { this(BLACK); }
    private Color(int value) { this.value=value; }
    public static Color valueOf(int value) { return new Color(value); }
    public int toArgb() { return value; }
    public float red() { return red(value)/255f; }
    public float green() { return green(value)/255f; }
    public float blue() { return blue(value)/255f; }
    public float alpha() { return alpha(value)/255f; }
    public ColorSpace getColorSpace() { return ColorSpace.get(ColorSpace.Named.SRGB); }
    public ColorSpace.Model getModel() { return ColorSpace.Model.RGB; }
    public boolean isSrgb() { return true; }
    public boolean isWideGamut() { return false; }
    public int getComponentCount() { return 4; }
    public float getComponent(int index) {
        switch(index){case 0:return red();case 1:return green();case 2:return blue();case 3:return alpha();default:throw new ArrayIndexOutOfBoundsException(index);}
    }
    public float[] getComponents() { return new float[]{red(),green(),blue(),alpha()}; }
    public float[] getComponents(float[] target) {
        if(target==null)return getComponents();
        if(target.length<4)throw new IllegalArgumentException("Color requires four components");
        for(int i=0;i<4;i++)target[i]=getComponent(i);
        return target;
    }
    @Override public boolean equals(Object other) { return other instanceof Color&&((Color)other).value==value; }
    @Override public int hashCode() { return value; }
    public static int alpha(int color) { return color >>> 24; }
    public static int red(int color) { return (color >>> 16) & 255; }
    public static int green(int color) { return (color >>> 8) & 255; }
    public static int blue(int color) { return color & 255; }
    public static int rgb(int red, int green, int blue) { return argb(255, red, green, blue); }
    public static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
    public static int parseColor(String text) {
        if (text.charAt(0) == '#') {
            long value = Long.parseLong(text.substring(1), 16);
            if (text.length() == 7) return (int) value | 0xff000000;
            if (text.length() == 9) return (int) value;
        } else {
            Integer value = NAMES.get(text.toLowerCase(Locale.ROOT));
            if (value != null) return value;
        }
        throw new IllegalArgumentException("Unknown color");
    }
}
