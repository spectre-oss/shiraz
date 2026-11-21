// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

/** macOS CoreLocation (CLLocationManager) bridge; loaded only on Darwin hosts. */
public final class DarwinLocation {
    public static final int STATUS_UNAVAILABLE = 0;
    public static final int STATUS_NOT_DETERMINED = 1;
    public static final int STATUS_RESTRICTED = 2;
    public static final int STATUS_DENIED = 3;
    public static final int STATUS_AUTHORIZED = 4;

    private static final boolean loaded;
    static {
        boolean ok = false;
        if (HostOs.darwin()) {
            try {
                System.loadLibrary("shiraz-location");
                ok = true;
            } catch (UnsatisfiedLinkError ignored) {
                ok = false;
            }
        }
        loaded = ok;
    }

    private DarwinLocation() {}

    public static boolean isAvailable() {
        return loaded;
    }

    public static int authorizationStatus() {
        return loaded ? nativeAuthorizationStatus() : STATUS_UNAVAILABLE;
    }

    public static boolean locationServicesEnabled() {
        return loaded && nativeLocationServicesEnabled();
    }

    public static long start(boolean fineAccuracy, double minDistanceMeters) {
        if (!loaded) {
            return 0L;
        }
        return nativeStart(fineAccuracy, minDistanceMeters);
    }

    public static void stop(long handle) {
        if (loaded && handle != 0L) {
            nativeStop(handle);
        }
    }

    public static double[] pollFix(long handle) {
        if (!loaded || handle == 0L) {
            return null;
        }
        return nativePollFix(handle);
    }

    public static String lastError(long handle) {
        return loaded ? nativeLastError(handle) : "CoreLocation library unavailable";
    }

    private static native int nativeAuthorizationStatus();

    private static native boolean nativeLocationServicesEnabled();

    private static native long nativeStart(boolean fineAccuracy, double minDistanceMeters);

    private static native void nativeStop(long handle);

    private static native double[] nativePollFix(long handle);

    private static native String nativeLastError(long handle);
}
