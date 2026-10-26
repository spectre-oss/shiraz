// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.res.Resources;
import android.graphics.Point;
import android.util.DisplayMetrics;

/** One logical display: the app's configured presentation buffer, not the host desktop. */
public final class Display {
    public static final int DEFAULT_DISPLAY = 0, INVALID_DISPLAY = -1;
    private final Resources resources;
    private Display(Resources resources) { this.resources = resources; }
    public static Display shirazForResources(Resources resources) {
        if (resources == null) throw new NullPointerException("resources");
        return new Display(resources);
    }
    /** Android reports RGBA_8888 for this deprecated query. */
    @Deprecated public int getPixelFormat(){return android.graphics.PixelFormat.RGBA_8888;}
    public int getFlags(){return 0;}
    public int getDisplayId() { return DEFAULT_DISPLAY; }
    public String getName() { return "Shiraz application display"; }
    public boolean isValid() {
        try { resources.getDisplayMetrics(); return true; } catch (IllegalStateException closed) { return false; }
    }
    public void getMetrics(DisplayMetrics output) {
        if (output == null) throw new NullPointerException("metrics");
        output.setTo(resources.getDisplayMetrics());
    }
    public void getRealMetrics(DisplayMetrics output) {
        if (output == null) throw new NullPointerException("metrics");
        output.setTo(resources.shirazBaseDisplayMetrics());
    }
    public int getWidth() { return resources.getDisplayMetrics().widthPixels; }
    public int getHeight() { return resources.getDisplayMetrics().heightPixels; }
    public void getSize(Point output) { if (output == null) throw new NullPointerException("size"); DisplayMetrics value = resources.getDisplayMetrics(); output.set(value.widthPixels, value.heightPixels); }
    public void getRectSize(android.graphics.Rect output){if(output==null)throw new NullPointerException("size");DisplayMetrics value=resources.getDisplayMetrics();output.set(0,0,value.widthPixels,value.heightPixels);}
    public void getCurrentSizeRange(Point smallest,Point largest){int lo=Math.min(getWidth(),getHeight()),hi=Math.max(getWidth(),getHeight());smallest.set(lo,lo);largest.set(hi,hi);}
    public void getRealSize(Point output) { getSize(output); }
    // The host presentation currently does no buffer rotation.
    public int getRotation() { resources.getDisplayMetrics(); return 0; }
    public int getOrientation() { return getRotation(); }
    public float getRefreshRate() { resources.getDisplayMetrics();return shiraz.runtime.NativeSurface.refreshRate(); }
    public Mode getMode(){return new Mode(getWidth(),getHeight(),getRefreshRate());}
    public Mode[] getSupportedModes(){return new Mode[]{getMode()};}
    public boolean isHdr(){return false;}
    public HdrCapabilities getHdrCapabilities(){return new HdrCapabilities();}
    public static final class HdrCapabilities {
        public static final float INVALID_LUMINANCE=-1;
        public int[] getSupportedHdrTypes(){return new int[0];}
        public float getDesiredMaxLuminance(){return INVALID_LUMINANCE;}
        public float getDesiredMaxAverageLuminance(){return INVALID_LUMINANCE;}
        public float getDesiredMinLuminance(){return INVALID_LUMINANCE;}
    }
    public static final class Mode {
        private final int width,height;private final float refresh;
        private Mode(int w,int h,float r){width=w;height=h;refresh=r;}
        public int getModeId(){return 1;}public int getPhysicalWidth(){return width;}public int getPhysicalHeight(){return height;}public float getRefreshRate(){return refresh;}
    }
}
