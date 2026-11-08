// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Packed and object sRGB colors. Other color spaces fail explicitly. */
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
    private final float r,g,b,a;
    public Color() { this(BLACK); }
    private Color(int value) { this.value=value;r=red(value)/255f;g=green(value)/255f;b=blue(value)/255f;a=alpha(value)/255f; }
    private Color(float red,float green,float blue,float alpha){r=pin(red);g=pin(green);b=pin(blue);a=pin(alpha);value=argb(Math.round(a*255),Math.round(r*255),Math.round(g*255),Math.round(b*255));}
    public static Color valueOf(int value) { return new Color(value); }
    public static Color valueOf(float red,float green,float blue,float alpha,ColorSpace space) {
        if(space==null)throw new NullPointerException("colorSpace");
        if(!space.isSrgb())throw new UnsupportedOperationException("Software raster supports only sRGB");
        return new Color(red,green,blue,alpha);
    }
    public long pack(){return (value&0xffffffffL)<<32;}
    public int toArgb() { return value; }
    public float red() { return r; }
    public float green() { return g; }
    public float blue() { return b; }
    public float alpha() { return a; }
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
    @Override public boolean equals(Object other) { if(!(other instanceof Color))return false;Color color=(Color)other;return Float.compare(r,color.r)==0&&Float.compare(g,color.g)==0&&Float.compare(b,color.b)==0&&Float.compare(a,color.a)==0; }
    @Override public int hashCode() { return java.util.Arrays.hashCode(getComponents()); }
    public static int alpha(int color) { return color >>> 24; }
    public static int red(int color) { return (color >>> 16) & 255; }
    public static int green(int color) { return (color >>> 8) & 255; }
    public static int blue(int color) { return color & 255; }
    public static int rgb(int red, int green, int blue) { return argb(255, red, green, blue); }
    public static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
    private static float pin(float value){return Math.max(0,Math.min(1,value));}
    public static void colorToHSV(int color,float[] hsv){RGBToHSV(red(color),green(color),blue(color),hsv);}
    public static void RGBToHSV(int red,int green,int blue,float[] hsv){
        if(hsv.length<3)throw new RuntimeException("3 components required for hsv");
        float r=red/255f,g=green/255f,b=blue/255f,max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),delta=max-min,hue=0;
        if(delta!=0){if(max==r)hue=60*((g-b)/delta);else if(max==g)hue=60*(2+(b-r)/delta);else hue=60*(4+(r-g)/delta);if(hue<0)hue+=360;}
        hsv[0]=hue;hsv[1]=max==0?0:delta/max;hsv[2]=max;
    }
    public static int HSVToColor(float[] hsv){return HSVToColor(255,hsv);}
    public static int HSVToColor(int alpha,float[] hsv){
        if(hsv.length<3)throw new RuntimeException("3 components required for hsv");
        float hue=hsv[0];if(!(hue>=0&&hue<360))hue=0;
        float saturation=pin(hsv[1]),value=pin(hsv[2]),chroma=value*saturation;
        float x=chroma*(1-Math.abs((hue/60)%2-1)),m=value-chroma,r=0,g=0,b=0;
        switch((int)(hue/60)){case 0:r=chroma;g=x;break;case 1:r=x;g=chroma;break;case 2:g=chroma;b=x;break;case 3:g=x;b=chroma;break;case 4:r=x;b=chroma;break;default:r=chroma;b=x;}
        return argb(alpha,Math.round((r+m)*255),Math.round((g+m)*255),Math.round((b+m)*255));
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
