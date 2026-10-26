// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
/** Device configuration facts. reqGlEsVersion is filled from a created EGL context. */
public class ConfigurationInfo {
    public int reqTouchScreen, reqKeyboardType, reqNavigation, reqInputFeatures, reqGlEsVersion;
    public String getGlEsVersion() {
        return ((reqGlEsVersion & 0xffff0000) >> 16) + "." + (reqGlEsVersion & 0xffff);
    }
}
