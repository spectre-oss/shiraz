// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** Asynchronous callback interface receiving either a result or an error. */
public interface OutcomeReceiver<R, E extends Throwable> {
    void onResult(R result);

    default void onError(E error) {
    }
}
