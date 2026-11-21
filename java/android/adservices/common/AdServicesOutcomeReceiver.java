// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.common;

/** Callback interface for asynchronous AdServices operations. */
public interface AdServicesOutcomeReceiver<R, E extends Throwable> {
    void onResult(R result);

    default void onError(E error) {
    }
}
