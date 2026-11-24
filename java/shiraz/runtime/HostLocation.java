// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.location.Location;
import android.os.SystemClock;

/**
 * Cross-platform host location bridge.
 *
 * <p>On macOS, delegates to {@link DarwinLocation} (CoreLocation). On Linux, loads
 * {@code libshiraz-location.so} (GeoClue2 / XDG Desktop Portal Location via D-Bus).
 * Both backends share the exact same status codes and 7-element fix array contract.
 */
public final class HostLocation {
    public static final int STATUS_UNAVAILABLE = 0;
    public static final int STATUS_NOT_DETERMINED = 1;
    public static final int STATUS_RESTRICTED = 2;
    public static final int STATUS_DENIED = 3;
    public static final int STATUS_AUTHORIZED = 4;

    private static final boolean linuxLoaded;
    static {
        boolean ok = false;
        if (HostOs.linux() && !"1".equals(System.getenv("SHIRAZ_DISABLE_HOST_LOCATION"))) {
            try {
                System.loadLibrary("shiraz-location");
                ok = true;
            } catch (UnsatisfiedLinkError ignored) {
                ok = false;
            }
        }
        linuxLoaded = ok;
    }

    private static volatile HostLocationBackend testBackend;

    private HostLocation() {}

    public static void setBackendForTest(HostLocationBackend replacement) {
        testBackend = replacement;
    }

    private static HostLocationBackend activeTestBackend() {
        HostLocationBackend b = testBackend;
        if (b != null) {
            return b;
        }
        return DarwinLocation.getBackendForTest();
    }

    public static boolean isAvailable() {
        HostLocationBackend b = activeTestBackend();
        if (b != null) {
            return b.isAvailable();
        }
        if (HostOs.darwin()) {
            return DarwinLocation.isAvailable();
        }
        return linuxLoaded;
    }

    public static int authorizationStatus() {
        HostLocationBackend b = activeTestBackend();
        if (b != null) {
            return b.authorizationStatus();
        }
        if (HostOs.darwin()) {
            return DarwinLocation.authorizationStatus();
        }
        return linuxLoaded ? nativeAuthorizationStatus() : STATUS_UNAVAILABLE;
    }

    public static boolean locationServicesEnabled() {
        HostLocationBackend b = activeTestBackend();
        if (b != null) {
            return b.locationServicesEnabled();
        }
        if (HostOs.darwin()) {
            return DarwinLocation.locationServicesEnabled();
        }
        return linuxLoaded && nativeLocationServicesEnabled();
    }

    public static long start(boolean fineAccuracy, double minDistanceMeters) {
        HostLocationBackend b = activeTestBackend();
        if (b != null) {
            return b.start(fineAccuracy, minDistanceMeters);
        }
        if (HostOs.darwin()) {
            return DarwinLocation.start(fineAccuracy, minDistanceMeters);
        }
        return linuxLoaded ? nativeStart(fineAccuracy, minDistanceMeters) : 0L;
    }

    public static void stop(long handle) {
        if (handle == 0L) {
            return;
        }
        HostLocationBackend b = activeTestBackend();
        if (b != null) {
            b.stop(handle);
            return;
        }
        if (HostOs.darwin()) {
            DarwinLocation.stop(handle);
            return;
        }
        if (linuxLoaded) {
            nativeStop(handle);
        }
    }

    public static double[] pollFix(long handle) {
        if (handle == 0L) {
            return null;
        }
        HostLocationBackend b = activeTestBackend();
        if (b != null) {
            return b.pollFix(handle);
        }
        if (HostOs.darwin()) {
            return DarwinLocation.pollFix(handle);
        }
        return linuxLoaded ? nativePollFix(handle) : null;
    }

    public static String lastError(long handle) {
        HostLocationBackend b = activeTestBackend();
        if (b != null) {
            return b.lastError(handle);
        }
        if (HostOs.darwin()) {
            return DarwinLocation.lastError(handle);
        }
        return linuxLoaded ? nativeLastError(handle) : "Host location library unavailable";
    }

    /** Converts a 7-element host location fix array into an Android Location. */
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
