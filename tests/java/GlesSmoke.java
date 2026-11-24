// SPDX-License-Identifier: GPL-3.0-only
import android.app.ActivityManager;
public final class GlesSmoke {
    public static void main(String[] args) throws Exception {
        Class<?> egl11=Class.forName("javax.microedition.khronos.egl.EGL11");
        if(!egl11.isInstance(javax.microedition.khronos.egl.EGLContext.getEGL()) || egl11.getField("EGL_CONTEXT_LOST").getInt(null)!=0x300e)throw new AssertionError("EGL11 API and context-loss constant");
        if (ActivityManager.class.getMethod("getDeviceConfigurationInfo").getReturnType() != android.content.pm.ConfigurationInfo.class) throw new AssertionError("GLES configuration type");
        android.content.pm.ConfigurationInfo info = ActivityManager.shirazForProcess("gles").getDeviceConfigurationInfo();
        int major = (info.reqGlEsVersion & 0xffff0000) >> 16;
        if (info.reqGlEsVersion != 0 && major != 3) throw new AssertionError("Unexpected GLES version " + Integer.toHexString(info.reqGlEsVersion));
        android.content.pm.PackageManager pm = new android.content.pm.PackageManager() {
            @Override public android.content.pm.ActivityInfo getActivityInfo(android.content.ComponentName c, int f) { return null; }
            @Override public android.content.pm.ServiceInfo getServiceInfo(android.content.ComponentName c, int f) { return null; }
            @Override public android.content.pm.PackageInfo getPackageInfo(String p, int f) { return null; }
            @Override public android.content.pm.ApplicationInfo getApplicationInfo(String p, int f) { return null; }
            @Override public CharSequence getApplicationLabel(android.content.pm.ApplicationInfo i) { return ""; }
        };
        if (!"android.hardware.vulkan.level".equals(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
                || !"android.hardware.vulkan.version".equals(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
                || !"android.hardware.vulkan.compute".equals(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE)) {
            throw new AssertionError("Vulkan feature constants");
        }
        boolean hasVkLevel = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL);
        boolean hasVkVersion = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION);
        if (hasVkLevel != hasVkVersion) throw new AssertionError("Vulkan level/version mismatch");
        if (hasVkLevel && !pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x400000)) {
            throw new AssertionError("Vulkan version must be >= 1.0 when level is reported");
        }
        if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x7fffffff)) {
            throw new AssertionError("Impossible Vulkan version reported");
        }
        System.out.println("GLES_VERSION " + Integer.toHexString(info.reqGlEsVersion) + " VULKAN=" + (hasVkLevel ? 1 : 0));
    }
}
