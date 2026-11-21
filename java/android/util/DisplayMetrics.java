// SPDX-License-Identifier: GPL-3.0-only
package android.util;

public class DisplayMetrics {
    public static final int DENSITY_DEFAULT = 160, DENSITY_LOW = 120, DENSITY_MEDIUM = 160, DENSITY_HIGH = 240, DENSITY_XHIGH = 320;
    // The desktop virtual device has an mdpi base. Host window scaling changes
    // densityDpi, not this device identity; service and activity processes agree.
    public static final int DENSITY_DEVICE_STABLE = DENSITY_DEFAULT;
    public int widthPixels, heightPixels, densityDpi = DENSITY_DEFAULT;
    public float density = 1, scaledDensity = 1, xdpi = 160, ydpi = 160;
    public DisplayMetrics() { }
    public void setTo(DisplayMetrics other) {
        widthPixels = other.widthPixels; heightPixels = other.heightPixels; densityDpi = other.densityDpi;
        density = other.density; scaledDensity = other.scaledDensity; xdpi = other.xdpi; ydpi = other.ydpi;
    }
}
