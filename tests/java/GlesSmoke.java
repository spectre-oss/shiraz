// SPDX-License-Identifier: GPL-3.0-only
import android.app.ActivityManager;
public final class GlesSmoke {
    public static void main(String[] args) throws Exception {
        if (ActivityManager.class.getMethod("getDeviceConfigurationInfo").getReturnType() != android.content.pm.ConfigurationInfo.class) throw new AssertionError("GLES configuration type");
        android.content.pm.ConfigurationInfo info = ActivityManager.shirazForProcess("gles").getDeviceConfigurationInfo();
        int major = (info.reqGlEsVersion & 0xffff0000) >> 16;
        if (info.reqGlEsVersion != 0 && major != 3) throw new AssertionError("Unexpected GLES version " + Integer.toHexString(info.reqGlEsVersion));
        System.out.println("GLES_VERSION " + Integer.toHexString(info.reqGlEsVersion));
    }
}
