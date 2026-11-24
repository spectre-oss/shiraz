// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

/** Injectable host location backend contract shared across macOS, Linux, and Windows. */
public interface HostLocationBackend {
    boolean isAvailable();

    int authorizationStatus();

    boolean locationServicesEnabled();

    long start(boolean fineAccuracy, double minDistanceMeters);

    void stop(long handle);

    double[] pollFix(long handle);

    String lastError(long handle);
}
