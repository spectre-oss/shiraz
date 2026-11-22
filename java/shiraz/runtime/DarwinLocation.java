// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.location.Location;
import android.os.SystemClock;

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

    private static volatile DarwinLocationBackend testBackend;

    private DarwinLocation() {}

    public static void setBackendForTest(DarwinLocationBackend replacement) {
        testBackend = replacement;
    }

    public static boolean isAvailable() {
        DarwinLocationBackend b = testBackend;
        return b != null ? b.isAvailable() : loaded;
    }

    public static int authorizationStatus() {
        DarwinLocationBackend b = testBackend;
        if (b != null) {
            return b.authorizationStatus();
        }
        return loaded ? nativeAuthorizationStatus() : STATUS_UNAVAILABLE;
    }

    public static boolean locationServicesEnabled() {
        DarwinLocationBackend b = testBackend;
        if (b != null) {
            return b.locationServicesEnabled();
        }
        return loaded && nativeLocationServicesEnabled();
    }

    public static long start(boolean fineAccuracy, double minDistanceMeters) {
        DarwinLocationBackend b = testBackend;
        if (b != null) {
            return b.start(fineAccuracy, minDistanceMeters);
        }
        return loaded ? nativeStart(fineAccuracy, minDistanceMeters) : 0L;
    }

    public static void stop(long handle) {
        if (handle == 0L) {
            return;
        }
        DarwinLocationBackend b = testBackend;
        if (b != null) {
            b.stop(handle);
            return;
        }
        if (loaded) {
            nativeStop(handle);
        }
    }

    public static double[] pollFix(long handle) {
        if (handle == 0L) {
            return null;
        }
        DarwinLocationBackend b = testBackend;
        if (b != null) {
            return b.pollFix(handle);
        }
        return loaded ? nativePollFix(handle) : null;
    }

    public static String lastError(long handle) {
        DarwinLocationBackend b = testBackend;
        if (b != null) {
            return b.lastError(handle);
        }
        return loaded ? nativeLastError(handle) : "CoreLocation library unavailable";
    }

    /** Converts a 7-element CoreLocation fix array into an Android Location. */
    public static Location toLocation(String provider, double[] values) {
        if (values == null || values.length < 7) {
            return null;
        }
        Location loc = new Location(provider);
        loc.setLatitude(values[0]);
        loc.setLongitude(values[1]);
        loc.setAltitude(values[2]);
        if (values[3] >= 0.0 && Double.isFinite(values[3])) {
            loc.setAccuracy((float) values[3]);
        }
        if (values[4] >= 0.0 && Double.isFinite(values[4])) {
            loc.setSpeed((float) values[4]);
        }
        if (values[5] >= 0.0 && Double.isFinite(values[5])) {
            loc.setBearing((float) values[5]);
        }
        long epochMs = (values[6] > 0.0 && Double.isFinite(values[6]))
                ? (long) values[6]
                : System.currentTimeMillis();
        loc.setTime(epochMs);
        long elapsedNanos;
        try {
            elapsedNanos = SystemClock.elapsedRealtimeNanos();
        } catch (Throwable ignored) {
            elapsedNanos = System.nanoTime();
        }
        loc.setElapsedRealtimeNanos(elapsedNanos);
        return loc;
    }

    private static native int nativeAuthorizationStatus();

    private static native boolean nativeLocationServicesEnabled();

    private static native long nativeStart(boolean fineAccuracy, double minDistanceMeters);

    private static native void nativeStop(long handle);

    private static native double[] nativePollFix(long handle);

    private static native String nativeLastError(long handle);
}
