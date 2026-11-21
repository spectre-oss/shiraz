// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.mbms;

import java.util.List;

public interface MbmsGroupCallSessionCallback {
    default void onError(int errorCode, String message) {}

    default void onAvailableSaisUpdated(
            List<Integer> currentSais, List<List<Integer>> availableSais) {}

    default void onServiceInterfaceAvailable(String interfaceName, int index) {}

    default void onMiddlewareReady() {}
}
